// https://ccrma.stanford.edu/groups/soundwire/software/jacktrip/
// http://qjackctl.sourceforge.net/
// http://sites.google.com/site/jacktripdocumentation/the-team
/*
	jack_connect system:playback_1 netjack:capture_1
jack_connect system:playback_2 netjack:capture_2

jack_lsp lists available ports
*/

/*
	This assumes jackd is running, and has the correct number of virtual channels
*/

UtopiaJackTrip {
	var addrBook, numChannelsPerUser, disconnectClientsFromSystem, <bitRes;
	var myServers, myClients, addrs, portOffsets, numActivePings = 0;

	classvar pathsInited = false, <>jacktripPath, <>jackconnectPath, <>jackdisconnectPath, pids;

	*new {|addrBook, numChannelsPerUser = 1, disconnectClientsFromSystem = true, bitRes = 32|
		^super.newCopyArgs(addrBook, numChannelsPerUser, disconnectClientsFromSystem, bitRes).init;
	}

	*initPaths {
		var pipe;
		// jacktrip
		if(jacktripPath.isNil, {
			pipe = Pipe("which jacktrip", "r"); // try PATH
			jacktripPath = pipe.getLine;
			pipe.close;
			if(jacktripPath.isNil, {
				// search in /usr/ then give up
				pipe = Pipe("find /usr -type f -name jacktrip", "r");
				jacktripPath = pipe.getLine;
				pipe.close;
			});
		});

		// jack_connect

		if(jackconnectPath.isNil, {
			pipe = Pipe("which jack_connect", "r"); // try PATH
			jackconnectPath = pipe.getLine;
			pipe.close;
			if(jackconnectPath.isNil, {
				// search in /usr/ then give up
				pipe = Pipe("find /usr -type f -name jack_connect", "r");
				jackconnectPath = pipe.getLine;
				pipe.close;
			});
		});

		// jack_disconnect
		if(jackdisconnectPath.isNil, {
			pipe = Pipe("which jack_disconnect", "r"); // try PATH
			jackdisconnectPath = pipe.getLine;
			pipe.close;
			if(jackdisconnectPath.isNil, {
				// search in /usr/ then give up
				pipe = Pipe("find /usr -type f -name jack_disconnect", "r");
				jackdisconnectPath = pipe.getLine;
				pipe.close;
			});
		});

		"jacktrip Path: %\njack_connect path: %\njack_disconnect path: %\n\n".postf(jacktripPath, jackconnectPath, jackdisconnectPath);
		if(jacktripPath.isNil || jackconnectPath.isNil || jackdisconnectPath.isNil, {
			"Unable to find jack cmdline tools. Try setting paths manually.".warn;
		}, {
			pathsInited = true;
		});
	}

	init {
		var sortedKeys, myIndex;
		pathsInited.not.if({
			this.class.initPaths;
			pathsInited.not.if({ ^nil }); // bail
		});
		addrs = addrBook.peers;
		sortedKeys = SortedList.new(addrs.size, {|a, b|
			a.asString.compare(b.asString.asString) <= 0 }).addAll(addrBook.names);
		myIndex = sortedKeys.indexOf(addrBook.me.name);
		myServers = sortedKeys.copyToEnd(myIndex + 1);
		myClients = sortedKeys.copyFromStart(myIndex - 1);
		this.calcPortOffsets;
		this.launchServers(myIndex);
		this.waitToLaunchClients(myIndex);
		ShutDown.add({this.class.quit});
	}

	calcPortOffsets {
		var numConnections;
		// triangular number n(n + 1) / 2
		numConnections = ((addrs.size - 1) * addrs.size) / 2;
		portOffsets = Array.series(numConnections, 0, 10)
			.clumps(Array.series(addrs.size - 1, addrs.size - 1, -1));
	}

	launchServers {|myIndex|
		var myServerPorts;
		myServerPorts = portOffsets[myIndex];
		myServers.do({|nickname, i|
			"///// UtopiaJackTrip launching server for client %\n\n".postf(nickname);
			pids = pids ++ [("% -s -b% -n% --clientname % -o%".format(jacktripPath, bitRes, numChannelsPerUser, nickname, myServerPorts[i]).postln.unixCmd)];
			this.pingForClient(nickname, myServerPorts[i]);
		});
	}

	pingForClient {|nickname, port|
		var pingTask;
		numActivePings = numActivePings + 1;
		OSCFunc({|msg| // msg is [/launchJackTripClient, serverName, port]
				"///// UtopiaJackTrip client for % has been launched\n\n".postf(nickname);
				numActivePings = numActivePings - 1;
				pingTask.stop;
				if(numActivePings == 0, { SystemClock.sched(1.5, {this.makeConnections}) });
		}, '/clientLaunched', addrBook[nickname.asSymbol].addr).oneShot;

		pingTask = Task({
			loop {
				addrBook.send(nickname, '/launchJackTripClient', addrBook.me.name, port);
				0.5.wait;
			}
		}).play;
	}

	waitToLaunchClients {|myIndex|
		myClients.do({|nickname|
			OSCFunc({|msg| // msg is [/launchJackTripClient, serverName, port]
				"///// UtopiaJackTrip launching client for server %\n\n".postf(msg[0]);
				pids = pids ++ [("% -c % -b% -n% --clientname % -o%".format(jacktripPath, addrBook[nickname].addr.ip, bitRes, numChannelsPerUser, msg[1], msg[2]).postln.unixCmd)];
				addrBook.send(msg[1].asSymbol, '/clientLaunched', addrBook.me.name);
				if(numActivePings == 0, { SystemClock.sched(1.5, {this.makeConnections}) });
			}, '/launchJackTripClient', addrBook[nickname.asSymbol].addr).oneShot;
		});

	}

	makeConnections {
		"///// UtopiaJackTrip attempting to make connections\n".postln;
		{
		// remove system connections if desired
		disconnectClientsFromSystem.if({
			"///// UtopiaJackTrip disconnecting utopia jacktrip instances from system\n".postln;
			(myServers ++ myClients).do({|nickname, i|
				numChannelsPerUser.do({|j|
					"% system:capture_% %:send_%"
						.format(jackdisconnectPath, j + 1, nickname, j+1).postln.systemCmd;
					0.2.wait;
					"% system:playback_% %:receive_%"
						.format(jackdisconnectPath, j + 1, nickname, j+1).postln.systemCmd;
					0.2.wait;
				});
			});
		});

		// sends
		(myServers ++ myClients).do({|nickname, i|
			numChannelsPerUser.do({|j|
				"% scsynth:out% %:send_%".format(jackconnectPath, j+1, nickname, j+1).postln.systemCmd;
				0.2.wait;
			});
		});

		// receives
		(myServers ++ myClients).do({|nickname, i|
			numChannelsPerUser.do({|j|
				"% scsynth:in% %:receive_%"
					.format(jackconnectPath, (i*numChannelsPerUser) + j + 1, nickname, j+1).postln.systemCmd;
				0.2.wait;
			});
		});

		}.fork;

	}

	inputBusses {
		var inputOffset;
		inputOffset = Server.default.options.numOutputBusChannels;
		^(myServers ++ myClients).collectAs({|nickname, i|
			nickname -> Bus(\audio, i*numChannelsPerUser + inputOffset, numChannelsPerUser, Server.default);
		}, IdentityDictionary);
	}

	*connect {|portA, portB|
		pathsInited.not.if({
			this.initPaths;
			pathsInited.not.if({ ^nil }); // bail
		});
		"% % %".format(jackconnectPath, portA, portB).postln.systemCmd;
	}

	*disconnect {|portA, portB|
		pathsInited.not.if({
			this.initPaths;
			pathsInited.not.if({ ^nil }); // bail
		});
		"% % %".format(jackdisconnectPath, portA, portB).postln.systemCmd;
	}

	*quit {
		pids.do({|pid|
			"kill -9 %".format(pid).unixCmd;
		});
	}
}
				