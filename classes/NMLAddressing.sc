// uses dependancy to update interested parties
// do we need the serverAddr, or could this be handled via a separate object/addrBook?
// nice thing about ServerAddr is it could actually be on another machine
OSCitizen {
	var <name, <addr, <serverAddr, <online;

	*new {|name, addr, serverAddr, online = true|
		^super.newCopyArgs(name.asSymbol, addr, serverAddr, online);
	}

	online_ {|bool| if(bool != online, { online = bool; this.changed(\online) }) }

	== {|other|
		var result;
		result = (name == other.name) && (addr == other.addr) && (serverAddr == other.serverAddr) && (online == other.online);
		^result;
	}

	// post pretty
	printOn { |stream|
		stream << this.class.name << "(" <<* [name, addr, serverAddr, online] << ")"
	}
}

// can we make this so that if there is no me we have a centralised system?
// or everything via OSC, so that there's no distinction?
AddrBook {
	var dict, <me;

	*new { ^super.new.init }

	init { dict = IdentityDictionary.new; }

	send {|name ...msg| dict[name].addr.sendMsg(*msg) }

	sendAll {|...msg| dict.do({|citizen| citizen.addr.sendMsg(*msg); }); }

	sendExcluding {|name ...msg| dict.reject({|cit, citName| citName == name }).do({|citizen| citizen.addr.sendMsg(*msg); });}

	add {|oscitizen| dict[oscitizen.name] = oscitizen; oscitizen.addDependant(this); this.changed(\add, oscitizen) }

	addMe {|meCitizen|
		meCitizen = meCitizen ?? {
			var name;
			name = "whoami".unixCmdGetStdOut;
			if(name.last == Char.nl, {name = name.drop(-1)});
			OSCitizen(name, NetAddr.localAddr, Server.default.addr)};
		this.add(meCitizen);
		me = meCitizen;
	}

	at {|name| ^dict[name] }

	remove {|oscitizen| dict[oscitizen.name] = nil; oscitizen.removeDependant(this); this.changed(\remove, oscitizen) }

	removeAt {|name| this.remove(dict[name]) }

	update {|changed, what| this.changed(what, changed) }

	names { ^dict.keys }

	addrs { ^dict.values.collect({|citizen| citizen.addr }) }

	citizens { ^dict.values }

	serverAddrs { ^dict.values.collect({|citizen| citizen.serverAddr }) }
}

// who's there?
Attendance {
	var <addrBook, period, oscPath, me, inOSCFunc, outOSCFunc, lastResponses;

	*new { |addrBook, period = 1.0, me, oscPath = '/attendance'|
		addrBook = addrBook ?? { AddrBook.new };
		^super.newCopyArgs(addrBook, period, oscPath).init(me);
	}

	// not totally sure about this me business...
	init {|argMe|
		if(argMe.notNil, {addrBook.addMe(argMe)}, { if(addrBook.me.isNil, {addrBook.addMe }) });
		me = addrBook.me;
		lastResponses = IdentityDictionary.new;
		this.makeOSCFuncs;
		this.takeAttendance;
	}

	makeOSCFuncs {
		var replyPath;
		replyPath = (oscPath ++ "-reply").asSymbol;
		inOSCFunc = OSCFunc({|msg, time, addr|
			var name, serverAddr;
			name = msg[1];
			serverAddr = msg[2];
			if(lastResponses[name].isNil, {
				addrBook.add(OSCitizen(name, addr, serverAddr));
			});
			addrBook[name].online = true;
			lastResponses[name] = time;
		}, replyPath, recvPort: addrBook.me.addr.port);

		outOSCFunc = OSCFunc({|msg, time, addr|
			addr.sendMsg(replyPath, me.name, me.serverAddr);
		}, oscPath, recvPort: addrBook.me.addr.port);
	}

	free { inOSCFunc.free; outOSCFunc.free; }

	takeAttendance {
		var broadcastAddr;
		NetAddr.broadcastFlag = true;
		broadcastAddr = NetAddrMP("255.255.255.255", 57120 + (0..7));
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
	var <addrBook, period, oscPath, lastResponses, pingRegistrarOSCFunc, registerOSCFunc, unRegisterOSCFunc, pingReplyOSCFunc;

	*new { |addrBook, period = 1.0, oscPath = '/register'|
		addrBook = addrBook ?? { AddrBook.new };
		^super.newCopyArgs(addrBook, period, oscPath).init;
	}

	init {
		lastResponses = IdentityDictionary.new;
		this.makeOSCFuncs;
		period.notNil.if({ this.ping; });
	}

	makeCitizen {|addr, name, serverIP, serverPort|
		^OSCitizen(name, addr, NetAddr(serverIP.asString, serverPort));
	}

	makeOSCFuncs {
		// people are looking for me
		pingRegistrarOSCFunc = OSCFunc({|msg, time, addr|
			addr.sendMsg(oscPath ++ "-pingRegistrarReply"); // I'm here!
		}, oscPath ++ "-pingRegistrar");

		registerOSCFunc = OSCFunc({|msg, time, addr|
			var citizen;
			citizen = this.makeCitizen(*([addr] ++ msg[1..]));
			// tell everyone about the new arrival
			addrBook.sendAll(oscPath ++ "-add", citizen.name, addr.ip, addr.port, citizen.serverAddr.hostname, citizen.serverAddr.port);
			// tell the new arrival about everyone
			addrBook.citizens.do({|cit|
				addr.sendMsg(oscPath ++ "-add", cit.name, cit.addr.ip, cit.addr.port, cit.serverAddr.hostname, cit.serverAddr.port);
			});
			addrBook.add(citizen);
		}, oscPath);

		unRegisterOSCFunc = OSCFunc({|msg, time, addr|
			var name;
			name = msg[1];
			addrBook.removeAt(name);
			lastResponses[name] = nil;
			addrBook.sendExcluding(name, oscPath ++ "-remove", name);
		}, oscPath ++ "-unregister");

		// make sure everyone is still online
		pingReplyOSCFunc = OSCFunc({|msg, time, addr|
			var name, citizen;
			name = msg[1];
			citizen = addrBook[name];
			citizen.notNil.if({
				citizen.online_(true);
				lastResponses[name] = time;
				addrBook.sendAll(oscPath ++ "-online", name, true.binaryValue);
			});
		}, oscPath ++ "-pingReply");
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
	var <addrBook, registrarAddr, oscPath, me, addOSCFunc, removeOSCFunc, onlineOSCFunc, pingOSCFunc, pinging;

	*new { |addrBook, me, registrarAddr, oscPath = '/register'|
		addrBook = addrBook ?? { AddrBook.new };
		^super.newCopyArgs(addrBook, registrarAddr, oscPath).init(me);
	}

	// not totally sure about this me business...
	init {|argMe|
		if(argMe.notNil, {addrBook.addMe(argMe)}, { if(addrBook.me.isNil, {addrBook.addMe }) });
		me = addrBook.me;
		this.addOSCFuncs;
		if(registrarAddr.isNil, { this.pingRegistrar }, { this.register });
	}

	makeCitizen {|name, hostname, port, serverHostname, serverPort|
		^OSCitizen(name, NetAddr(hostname.asString, port), NetAddr(serverHostname.asString, serverPort));
	}

	addOSCFuncs {
		addOSCFunc = OSCFunc({|msg, time, addr|
			var citizen;
			citizen = this.makeCitizen(*msg[1..]);
			addrBook.add(citizen);
		}, oscPath ++ "-add", registrarAddr, recvPort: addrBook.me.addr.port);

		removeOSCFunc = OSCFunc({|msg, time, addr|
			var name;
			name = msg[1];
			addrBook.removeAt(name);
		}, oscPath ++ "-remove", registrarAddr, recvPort: addrBook.me.addr.port);

		onlineOSCFunc = OSCFunc({|msg, time, addr|
			var name, citizen;
			name = msg[1];
			citizen = addrBook[name];
			citizen.notNil.if({ citizen.online_(msg[2].booleanValue) });
		}, oscPath ++ "-online", registrarAddr, recvPort: addrBook.me.addr.port);

		pingOSCFunc = OSCFunc({|msg, time, addr|
			registrarAddr.sendMsg(oscPath ++ "-pingReply", me.name);
		}, oscPath ++ "-ping", registrarAddr, recvPort: addrBook.me.addr.port);
	}

	free { pinging = false; this.unregister; addOSCFunc.free; removeOSCFunc.free; onlineOSCFunc.free; pingOSCFunc.free; }

	register {
		registrarAddr.sendMsg(oscPath, me.name, me.serverAddr.hostname, me.serverAddr.port);
	}

	unregister {
		registrarAddr.sendMsg(oscPath ++ "-unregister", me.name);
	}

	// automatically search for registrar...
	pingRegistrar {
		var broadcastAddr, registrarPingOSCFunc;
		pinging = true;
		NetAddr.broadcastFlag = true;
		broadcastAddr = NetAddrMP("255.255.255.255", 57120 + (0..7));
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
