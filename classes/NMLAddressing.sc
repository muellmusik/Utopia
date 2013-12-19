// not sure about addMe business...
// registrar uses loopback option immediately so address changes once registered; does this matter
// might be nice if addition to the AddrBook only took place once registered

// uses dependancy to update interested parties
// Peer spoofs a NetAddr for sending purposes

Peer {
	var <name, <addr, <online;

	*new {|name, addr, online = true|
		^super.newCopyArgs(name.asSymbol, addr, online);
	}

	*newFrom { |item|
		if(item.isKindOf(this)) { ^item };
		item = item ?? { this.defaultName };
		^this.new(item, LocalNetAddr("127.0.0.1", NetAddr.langPort))
	}

	*defaultName {
		^"whoami".unixCmdGetStdOut.replace("\n", "");
	}

	online_ {|bool| if(bool != online, { online = bool; this.changed(\online) }) }

	== {|other|
		^this.name == other.name && {this.addr.matches(other.addr)};
	}

	hash {
		^this.instVarHash(#[\name, \addr, \online])
	}

	// post pretty
	printOn { |stream|
		stream << this.class.name << "(" <<* [name, addr, online] << ")"
	}

	sendRaw { arg rawArray;
		addr.sendRaw(rawArray);
	}

	sendMsg { arg ... args;
		addr.sendMsg(*args);
	}

	sendBundle { arg time ... args;
		addr.sendBundle(time, *args);
	}

	sendClumpedBundles { arg time ... args;
		addr.sendClumpedBundles(time, *args);
	}
}

LocalNetAddr : NetAddr {
	var <alternateNames;

	*new { arg hostname = "127.0.0.1", port=0;
		^super.new(hostname, port).init;
	}

	init {
		alternateNames = Set.new;
		if(thisProcess.platform.name == \osx, { // should add for other platforms as well
			["en0", "en1"].do({|interface|
				var hostname = "ipconfig getifaddr %".format(interface).unixCmdGetStdOut;
				if(hostname.size > 0, {alternateNames.add(hostname)});
			});
		});
	}

	addName {|name| alternateNames.add(name); }

	== {|that| ^(super == that) || (alternateNames.includesEqual(that.hostName) && (that.port == this.port)) }

}

AddrBook {
	var dict, <me;

	*new { ^super.new.init }

	init { dict = IdentityDictionary.new; }

	send {|name ...msg| dict[name].addr.sendMsg(*msg) }

	add {|peer|
		peer = peer.as(Peer);
		dict[peer.name] = peer;
		peer.addDependant(this);
		this.changed(\add, peer)
	}

	addMe {|mePeer|
		mePeer = mePeer.as(Peer);
		this.add(mePeer);
		me = mePeer;
	}

	at {|name| ^dict[name] }

	remove {|peer| dict[peer.name] = nil; peer.removeDependant(this); this.changed(\remove, peer) }

	removeAt {|name| this.remove(dict[name]) }

	update {|changed, what| this.changed(what, changed) }

	names { ^dict.keys }

	addrs { ^dict.values.collect({|peer| peer.addr }) }

	peers { ^dict.values }

	onlinePeers { ^dict.reject({|peer| peer.online.not }).values }

	asPeerGroup { ^AllPeerGroup(this) }

	asOthersPeerGroup { ^AllNotMePeerGroup(this) }

	asAddrBook { }
}

// a Dict like class that provides a way to register Servers automatically between a number of Peers
ServerRegistry {
	var addrBook, myServer, options, oscPath, oscDataSpace, serverDict;

	// maybe should have an encryptor as well
	*new {|addrBook, myServer, options, oscPath = '/serverRegistry'|
		^super.newCopyArgs(addrBook, myServer ?? {Server.default}, options, oscPath).addDataSpace;
	}

	addDataSpace {
		serverDict = IdentityDictionary.new;
		serverDict[addrBook.me.name] = myServer;
		oscDataSpace = OSCDataSpace(addrBook, oscPath); // a dataspace of server ports
		oscDataSpace[addrBook.me.name] = myServer.addr.port;
		oscDataSpace.addDependant({|changed, what, name, port|
			if(what == \val, {
				// could also have peers send the options for their servers
				serverDict[name] = Server(name, NetAddr(addrBook[name].addr.ip, port), options, myServer.clientID);
			});
		});
	}

	at {|key| ^serverDict[key] }

	keys { ^serverDict.keys }

	values { ^serverDict.values }

	asAddrBook { ^addrBook }
}

AbstractPeerGroup {

	sendRaw { arg rawArray;
		this.targetCollection.do({|peer| peer.addr.sendRaw(rawArray)});
	}

	sendMsg { arg ... args;
		this.targetCollection.do({|peer| peer.addr.sendMsg(*args)});
	}

	sendBundle { arg time ... args;
		this.targetCollection.do({|peer| peer.addr.sendBundle(time, *args)});
	}

	sendClumpedBundles { arg time ... args;
		this.targetCollection.do({|peer| peer.addr.sendClumpedBundles(time, *args)});
	}

	// subclasses resolve sets of Peers or NetAddrs here
	targetCollection { this.subclassResponsibility(thisMethod); }
}

// this doesn't work with a regular dict!
AllPeerGroup : AbstractPeerGroup {
	var addrDict;

	*new {|addrDict| ^super.newCopyArgs(addrDict); }

	targetCollection { ^addrDict.peers }
}

// this doesn't work with a regular dict!
AllNotMePeerGroup : AbstractPeerGroup {
	var addrDict;

	*new {|addrDict| ^super.newCopyArgs(addrDict); }

	targetCollection { ^addrDict.peers.reject({|peer| peer.name == addrDict.me.name }) }
}


// PeerGroup spoofs a NetAddr
// a PeerGroup's GroupManager will keep the group synced
// it is essentially a proxy for a group of Peers or Servers
PeerGroup {
	var <name, manager;

	*new {|name, manager| ^super.newCopyArgs(name, manager) }

	targetCollection { ^manager.resolve(name) }
}

// addrDict could be any dictionary like object that understands the asAddrBook method, so probably an AddrBook or a ServerRegistry
GroupManager {
	var <addrDict, dataSpace;

	*new {|addrDict, oscPath = \groupsDataSpace|
		^super.newCopyArgs(addrDict).init(oscPath);
	}

	init { |oscPath| dataSpace = OSCDataSpace(addrDict, oscPath);  dataSpace.addDependant(this); }

	add { |groupname, names| dataSpace.put(groupname, names);  }

	remove { |groupname| dataSpace.put(groupname, nil); }

	// not sure about this
	// syntactically nice but maybe bad not to support put
	// Anyway, this allows lazy resolution
	at {|groupname| ^PeerGroup(groupname, this); }

	resolve {|groupname| ^addrDict[dataSpace[groupname]] }

	update { |… args| this.changed(args) } // register dependants to observe changes in groups
}

// Dispatcher {
// 	var <addrBook, <groupManager, <>verbose = false;
//
// 	*new {|addrBook, groupManager|
// 		groupManager = groupManager ?? { GroupManager(addrBook) };
// 		^super.newCopyArgs(addrBook, groupManager);
// 	}
//
// 	sendTo { |where ... msg|
// 		this.resolveWhere(where).do { |name|
// 			var addr = addrBook.at(name).addr;
// 			if(verbose) { [\where, where, \name, name, addr].postln };
// 			addr.sendMsg(*msg);
// 		};
// 	}
//
// 	/*	sendToServer { |where ... msg|
// 	this.resolveWhere(where).do { |name|
// 	var server = serverDict(name);
// 	if(verbose) { [\where, where, \name, name, server].postln };
// 	server.sendMsg(*msg);
// 	};
// 	}*/
//
// 	sendAll {|...msg| addrBook.addrs.do({|addr| addr.sendMsg(*msg); }); }
//
// 	sendAllBundle {|time ...msg| addrBook.addrs.do({|addr| addr.sendBundle(time, *msg); }); }
//
// 	sendExcluding {|name ...msg|
// 		addrBook.peers.reject({|peer| peer.name == name }).do({|peer| peer.addr.sendMsg(*msg); });
// 	}
//
// 	resolveWhere { |where|
// 		// \all = sendAll
//
// 		// expand list, flat to allow group substitution
// 		if (where.isSequenceableCollection) {
// 			^where.collect(this.resolveWhere(_)).flat.reject(_.isNil)
// 		};
// 		// nil = me
// 		if (where.isNil) { ^addrBook.me };
// 		//
// 		if (where === \all) { ^addrBook.peers };
//
// 		if (where.isKindOf(Symbol)) {
// 			// extant peer name: name
// 			if (addrBook[where].notNil) { ^addrBook[where] };
// 			// else might be a group
// 			if (groupManager[where].notNil) {
// 				^groupManager[where];
// 			};
// 			// none of the above - missing
// 			inform("AddrBook: Peer % is absent.".format(where));
// 			^nil
// 		};
//
// 		// index = nameList[index]
// 		if (where.isNumber) { ^addrBook.peers.wrapAt(where) };
//
// 		//			// maybe later?
// 		//			// location (point) = nearest peer by location
// 		//		if (where.isKindOf(Point)) {
// 		//			// find by location
// 		//		}
// 	}
//
// }

// who's there?
Hail {
	var <addrBook, period, oscPath, authenticator, me, inOSCFunc, outOSCFunc, lastResponses;

	*new { |addrBook, period = 1.0, me, authenticator, oscPath = '/hail'|
		addrBook = addrBook ?? { AddrBook.new };
		^super.newCopyArgs(addrBook, period, oscPath, authenticator).init(me);
	}

	// not totally sure about this me business...
	init {|argMe|
		if(argMe.notNil, {addrBook.addMe(argMe)}, { if(addrBook.me.isNil, {addrBook.addMe }) });
		me = addrBook.me;
		lastResponses = IdentityDictionary.new;
		authenticator = authenticator ?? { NonAuthenticator };
		this.makeOSCFuncs;
		this.hailingSignal;
	}

	makeOSCFuncs {
		var replyPath;
		replyPath = (oscPath ++ "-reply").asSymbol;
		inOSCFunc = OSCFunc({|msg, time, addr|
			var name, peer;
			name = msg[1];
			if(lastResponses[name].isNil, {
				peer = Peer(name, addr);
				authenticator.authenticate(peer, {
					addrBook.add(peer);
					addrBook[name].online = true;
					lastResponses[name] = time;
				});
			}, {
				addrBook[name].online = true;
				lastResponses[name] = time;
			});
		}, replyPath, recvPort: addrBook.me.addr.port).fix;

		outOSCFunc = OSCFunc({|msg, time, addr|
			addr.sendMsg(replyPath, me.name);
		}, oscPath, recvPort: addrBook.me.addr.port).fix;
	}

	free { inOSCFunc.free; outOSCFunc.free; }

	hailingSignal {
		var broadcastAddr;
		NetAddr.broadcastFlag = true;
		broadcastAddr = NMLNetAddrMP("255.255.255.255", 57120 + (0..7));
		SystemClock.sched(0, {
			broadcastAddr.sendMsg(oscPath);
			if(period.notNil, { this.checkOnline; });
			period;
		});
	}

	// everybody still there?
	checkOnline {
		var now;
		now = Main.elapsedTime;
		lastResponses.keysValuesDo({|name, lastHeardFrom|
			if((now - lastHeardFrom) > (period * 2), { addrBook[name].online = false });
		});
	}

}

// Centralised
Registrar {
	var <addrBook, period, authenticator, oscPath, lastResponses, pingRegistrarOSCFunc, registerOSCFunc, unRegisterOSCFunc, pingReplyOSCFunc;

	*new { |addrBook, period = 1.0, authenticator, oscPath = '/register'|
		addrBook = addrBook ?? { AddrBook.new };
		^super.newCopyArgs(addrBook, period, authenticator, oscPath).init;
	}

	init {
		lastResponses = IdentityDictionary.new;
		authenticator = authenticator ?? { NonAuthenticator };
		this.makeOSCFuncs;
		period.notNil.if({ this.ping; });
	}

	makePeer {|addr, name|
		^Peer(name, addr);
	}

	makeOSCFuncs {
		// people are looking for me
		pingRegistrarOSCFunc = OSCFunc({|msg, time, addr|
			addr.sendMsg(oscPath ++ "-pingRegistrarReply"); // I'm here!
		}, oscPath ++ "-pingRegistrar").fix;

		registerOSCFunc = OSCFunc({|msg, time, addr|
			var peer;
			peer = this.makePeer(addr, msg[1]);
			authenticator.authenticate(peer, {
				// tell everyone about the new arrival
				addrBook.sendAll(oscPath ++ "-add", peer.name, addr.ip, addr.port);
				// tell the new arrival about everyone
				addrBook.peers.do({|peer|
					addr.sendMsg(oscPath ++ "-add", peer.name, peer.addr.ip, peer.addr.port);
				});
				addrBook.add(peer);
			});
		}, oscPath).fix;

		unRegisterOSCFunc = OSCFunc({|msg, time, addr|
			var name;
			name = msg[1];
			addrBook.removeAt(name);
			lastResponses[name] = nil;
			addrBook.sendExcluding(name, oscPath ++ "-remove", name);
		}, oscPath ++ "-unregister").fix;

		// make sure everyone is still online
		pingReplyOSCFunc = OSCFunc({|msg, time, addr|
			var name, peer;
			name = msg[1];
			peer = addrBook[name];
			peer.notNil.if({
				peer.online_(true);
				lastResponses[name] = time;
				addrBook.sendAll(oscPath ++ "-online", name, true.binaryValue);
			});
		}, oscPath ++ "-pingReply").fix;
	}

	free { pingRegistrarOSCFunc.free; registerOSCFunc.free; unRegisterOSCFunc.free; pingReplyOSCFunc.free }

	ping {
		SystemClock.sched(0, {
			addrBook.sendAll(oscPath ++ "-ping");
			this.checkOnline;
			period;
		});
	}

	// everybody still there?
	checkOnline {
		var now;
		now = Main.elapsedTime;
		lastResponses.keysValuesDo({|name, lastHeardFrom|
			if((now - lastHeardFrom) > (period * 2), {
				addrBook[name].online = false;
				addrBook.sendAll(oscPath ++ "-online", name, false.binaryValue);
			});
		});
	}

}

Registrant {
	var <addrBook, registrarAddr, authenticator, oscPath, me, addOSCFunc, removeOSCFunc, onlineOSCFunc, pingOSCFunc, pinging;

	// we pass an authenticator here but maybe it's unnecessary. It's simply there to respond, not challenge in this case.
	*new { |addrBook, me, registrarAddr, authenticator, oscPath = '/register'|
		addrBook = addrBook ?? { AddrBook.new };
		^super.newCopyArgs(addrBook, registrarAddr, authenticator, oscPath).init(me);
	}

	// not totally sure about this me business...
	init {|argMe|
		if(argMe.notNil, {addrBook.addMe(argMe)}, { if(addrBook.me.isNil, {addrBook.addMe }) });
		me = addrBook.me;
		this.addOSCFuncs;
		if(registrarAddr.isNil, { this.pingRegistrar }, { this.register });
	}

	makePeer {|name, hostname, port|
		^Peer(name, NetAddr(hostname.asString, port));
	}

	addOSCFuncs {
		addOSCFunc = OSCFunc({|msg, time, addr|
			var peer;
			peer = this.makePeer(*msg[1..]);
			addrBook.add(peer);
		}, oscPath ++ "-add", registrarAddr, recvPort: addrBook.me.addr.port).fix;

		removeOSCFunc = OSCFunc({|msg, time, addr|
			var name;
			name = msg[1];
			addrBook.removeAt(name);
		}, oscPath ++ "-remove", registrarAddr, recvPort: addrBook.me.addr.port).fix;

		onlineOSCFunc = OSCFunc({|msg, time, addr|
			var name, peer;
			name = msg[1];
			peer = addrBook[name];
			peer.notNil.if({ peer.online_(msg[2].booleanValue) });
		}, oscPath ++ "-online", registrarAddr, recvPort: addrBook.me.addr.port).fix;

		pingOSCFunc = OSCFunc({|msg, time, addr|
			registrarAddr.sendMsg(oscPath ++ "-pingReply", me.name);
		}, oscPath ++ "-ping", registrarAddr, recvPort: addrBook.me.addr.port).fix;
	}

	free { pinging = false; this.unregister; addOSCFunc.free; removeOSCFunc.free; onlineOSCFunc.free; pingOSCFunc.free; }

	register {
		registrarAddr.sendMsg(oscPath, me.name);
	}

	unregister {
		registrarAddr.sendMsg(oscPath ++ "-unregister", me.name);
	}

	// automatically search for registrar...
	pingRegistrar {
		var broadcastAddr, registrarPingOSCFunc;
		pinging = true;
		NetAddr.broadcastFlag = true;
		broadcastAddr = NMLNetAddrMP("255.255.255.255", 57120 + (0..7));
		registrarPingOSCFunc = OSCFunc({|msg, time, addr|
			pinging = false;
			registrarAddr = addr;
			this.register;
		}, oscPath ++ "-pingRegistrarReply", recvPort: addrBook.me.addr.port).oneShot;

		{
			while( { pinging }, {
				broadcastAddr.sendMsg(oscPath ++ "-pingRegistrar");
				1.wait;
			});
		}.fork;
	}

}

// implements a NetAddr that can have multiple ports...
// this is the same as in Republic, but we duplicate here for now in order to avoid the dependancy
NMLNetAddrMP : NetAddr {

	var <>ports;

	*new { arg hostname, ports;
		ports = ports.asArray;
		^super.new(hostname, ports.first).ports_(ports)
	}

	sendRaw{ arg rawArray;
		ports.do{ |it|
			this.port_( it );
			^super.sendRaw( rawArray );
		}
	}

	sendMsg { arg ... args;
		ports.do{ |it|
			this.port_( it );
			super.sendMsg( *args );
		}
	}

	sendBundle { arg time ... args;
		ports.do{ |it|
			this.port_( it );
			super.sendBundle( *([time]++args) );
		}
	}
}

