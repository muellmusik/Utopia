// extensions to AddrBook for a rewrite of Republic
//

RepPeer : Peer {
	classvar defaultOptions;
	var <server;
	var <>location; // a point

	defaultOptions {
		^defaultOptions ?? {
			ServerOptions.new
			.numAudioBusChannels_(1024)
			.numControlBusChannels_(4096)
			.numInputBusChannels_(8)
			.numOutputBusChannels_(8)
			.memSize_(8192 * 32)
		}
	}

	peerID { ^if (server.notNil) { server.clientID } { -1 }; }

	// make them by hand now, should be automagic later
	makeServer { |clientID, port, options|
		port = port ? 56000;
		options = options ?? this.defaultOptions;
		[\makeServer, \name, name, \clientID, clientID].postln;
	 	server = Server(name,addr.copy.port_(port), options, clientID);
	 	server.latency = nil;
	 	if (server.isLocal) {
		 	server.waitForBoot({
			 	defer ({ server.initTree; }, 0.1)
			 });
		};
		^server
	}
}

RepHail : Hail {
	classvar <peerClass;
	var <broadcastAddr, <skipJack;
	var <>numBroadcastPorts = 4;
	var <>gracePeriods = 5;
//	var <>infoSendFunc, <>infoReceiveFunc;

	makeOSCFuncs {
		var replyPath;
		peerClass = peerClass ? RepPeer;
		replyPath = (oscPath ++ "-reply").asSymbol;
		inOSCFunc = OSCFunc({|msg, time, addr|
			var name, peer, peerID;

			name = msg[1];
			peerID = msg[2];

			if(lastResponses[name].isNil, {
				peer = peerClass.new(name, addr);
				authenticator.authenticate(peer, {
					addrBook.add(peer, peerID);
					addrBook[name].online = true;
					lastResponses[name] = time;
				});
			}, {
				addrBook[name].online = true;
				lastResponses[name] = time;
			});
		}, replyPath, recvPort: addrBook.me.addr.port).fix;

		outOSCFunc = OSCFunc({|msg, time, addr|
			addr.sendMsg(replyPath, me.name, me.peerID);
		}, oscPath, recvPort: addrBook.me.addr.port).fix;
	}

	hailingSignal {
		NetAddr.broadcastFlag = true;
		broadcastAddr = broadcastAddr ?? {
			NMLNetAddrMP("255.255.255.255", 57120 + (0..numBroadcastPorts - 1));
		};
		skipJack = skipJack ?? { SkipJack(
			{
				broadcastAddr.sendMsg(oscPath);
				if(period.notNil, { this.checkOnline; });
		}, period, false, "hailingSignal");
		};
	}

	start { this.hailingSignal; skipJack.play; }
	stop { skipJack.stop; }

		// everybody still there?
	checkOnline {
		var now;
		now = Main.elapsedTime;
		lastResponses.keysValuesDo({|name, lastHeardFrom|
			if((now - lastHeardFrom) > (period * gracePeriods), { addrBook[name].online = false });
		});
	}


	/*	hailingSignal {
	SystemClock.sched(0, {
	broadcastAddr.sendMsg(oscPath);
	if(period.notNil, { this.checkOnline; });
	period;
	});
	}*/

}

RepAddrBook : AddrBook {
	var <nameList;
	var <groups;

	init { super.init; nameList = List.new; groups = (); }

	// by default, add names in historical order
	add {|peer, peerID, port, options|
		if (dict[peer.name].isNil) {
			super.add(peer);

			["RepAddrBook.add", peer, peerID];

			if (nameList.includes(peer.name).not) { nameList.add(peer.name) };

			if (me.notNil) {
				// make server with peerID only if 'me' is there;
				// use peerID for server clientID.
				// so I will own the nodeID ranges for e.g. clientID 1 on all servers,
				// peer with clientID 2 will get the corresponding ranges etc
				peer.makeServer(me.peerID, port, options);
				// assume server is really running on the other machine
				peer.server.serverRunning_(true);
			};
		};
	}

	addMe {|mePeer, peerID, port, options|
		mePeer = mePeer.as(Peer);
		me = mePeer;
		this.add(mePeer, peerID, port, options);
		me.server.boot;
	}

	remove {|peer|
		super.remove(peer);
		nameList.remove(peer.name);
		// necessary to remove servers?
		//
	}

	addGroup { |groupname, names| groups.put(groupname, names); }

	removeGroup { |groupname| groups.removeAt(groupname); }

	names { ^nameList }

	myIndex { ^nameList.indexOf(me.name) }

	// always resolves to a single peer name, a flat list of names, or nil
	resolveWhere { |where|
		// \all = sendAll

		// expand list, flat to allow group substitution
		if (where.isKindOf(Array)) {
			^where.collect(this.resolveWhere(_)).flat.reject(_.isNil)
		};
		// nil = me
		if (where.isNil) { ^me.name };
		//
		if (where === \all) { ^nameList };

		if (where.isKindOf(Symbol)) {
			// extant peer name: name
			if (dict[where].notNil) { ^where };
			// else might be a group
			if (groups[where].notNil) {
				^groups[where].collect(this.resolveWhere(_)).reject(_.isNil);
			};
			// none of the above - missing
			inform("AddrBook: Peer % is absent.".format(where));
			^nil
		};


		// index = nameList[index]
		if (where.isNumber) { ^nameList.wrapAt(where + this.myIndex) };

		//			// maybe later?
		//			// location (point) = nearest peer by location
		//		if (where.isKindOf(Point)) {
		//			// find by location
		//		}
	}

	sendTo { |where ... msg|
		this.resolveWhere(where).do { |name|
			// only post for debugging
			dict.at(name).addr.postln.sendMsg(*msg);
		};
	}

	// use deep down in events
	sendToServer { |where ... msg|
		this.resolveWhere(where).do { |name|
			// only post for debugging
			dict.at(name).server.addr.postln.sendMsg(*msg);
		};
	}
}
