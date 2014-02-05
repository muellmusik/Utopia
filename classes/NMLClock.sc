// really very dumb master and slave clocks
// mostly for demonstration purposes
// these assume you have a shared timebase, e.g. via NTP

FollowerClock : Clock {
	var masterAddr, oscPath, scheduler, tickOSCFunc, <tempo = 1;
	var <beatsPerBar=4.0, barsPerBeat=0.25;
	var <baseBarBeat=0.0, <baseBar=0.0;

	*new {|masterAddr, oscPath = '/conductorClockTick'|
		^super.newCopyArgs(masterAddr, oscPath).init;
	}

	init {
		scheduler = Scheduler.new(this, drift:false, recursive:false);
		tickOSCFunc = OSCFunc({|msg, time, addr|
			var beatDelta, currentTempo;
			beatDelta = msg[1];
			currentTempo = msg[2];
			SystemClock.schedAbs(time, {tempo = currentTempo; this.tick(beatDelta)});
		}, oscPath, masterAddr).fix;
	}

	free { tickOSCFunc.free }

	clear {
		scheduler.clear;
	}

	secs2beats {|secs| ^secs }

	beats2secs {|beats| ^beats }

	sched { arg delta, item;
		scheduler.sched(delta, item);
	}
	schedAbs {|time, item|
		scheduler.schedAbs(time, item);
	}
	tick {|beatDelta|
		var saveClock = thisThread.clock;
		thisThread.clock = this;
		scheduler.advance(beatDelta);
		thisThread.clock = saveClock;
	}

	play { arg task, quant = 1;
		this.schedAbs(quant.nextTimeOnGrid(this).postln, task)
	}

	beatDur {
		^1/tempo;
	}
	beats {
		^scheduler.seconds;
	}
	nextTimeOnGrid { arg quant = 1, phase = 0;
		if (quant == 0) { ^this.beats + phase };
		if (quant < 0) { quant = beatsPerBar * quant.neg };
		if (phase < 0) { phase = phase % quant };
		^roundUp(this.beats - baseBarBeat - (phase % quant), quant) + baseBarBeat + phase
	}
}

ConductorClock {
	var addrBook, latency, granularity, oscPath, tempoClock;

	*new { |addrBook, latency = 0.05, granularity = 0.01, oscPath = '/conductorClockTick', tempo, beats, seconds, queueSize=256|
		^super.newCopyArgs(addrBook, latency, granularity, oscPath).init(tempo, beats, seconds, queueSize).startTicking;
	}

	init {|tempo, beats, seconds, queueSize|
		tempoClock = TempoClock(tempo, beats, seconds, queueSize);
	}

	tempo { ^tempoClock.tempo }

	tempo_ {|newTempo| tempoClock.tempo = newTempo; }

	startTicking {
		tempoClock.sched(0, {
			addrBook.sendAllBundle(latency, [oscPath, granularity, tempoClock.tempo]);
			granularity
		});
	}

	stop { tempoClock.stop; }

}

/// Pseudo Reference Broadcast Synchronisation Clock
/// These do not require a common timebase, and are self converging
/// However, you probably need at least three participants for this to work properly

/// this needs to keep track of which is last beacon
BeaconClock : TempoClock {
	var addrBook, beaconOSCFunc, compareOSCFunc, oscPath, compareDict;

	*new { |addrBook, tempo, beats, seconds, queueSize=256, oscPath = '/beaconClock'|
		if(addrBook.isNil, { "BeaconClock cannot work with nil AddrBook!".throw });
		^super.new(tempo, beats, seconds, queueSize).setVars(addrBook, oscPath).makeOSCFuncs.startBeacons;
	}

	setVars {|argAddrBook, argOSCPath| addrBook = argAddrBook; oscPath = argOSCPath; }

	startBeacons {
		var broadcastAddr, count = 0, myName, numReplies;
		// unusually we'll use broadcast here to avoid variations in send time
		NetAddr.broadcastFlag = true;
		broadcastAddr = NMLNetAddrMP("255.255.255.255", 57120 + (0..7));
		myName = addrBook.me.name;
		SystemClock.sched(rrand(0, 0.1) * addrBook.onlinePeers.size, { // what clock should this be on? Should it be permanent?
			//(myName ++ "sending Beacon").postln;
			// number of replies to expect
			numReplies = addrBook.onlinePeers.size;
			// only listen to my own beacons if I need to
			if(numReplies > 2, {numReplies = numReplies -1});
			broadcastAddr.sendMsg(oscPath, myName, count, numReplies);
			count = count + 1;
			(rrand(0.1, 0.2) * max(addrBook.onlinePeers.size, 1)); // minimum wait even if no other peers
		});
	}

	makeOSCFuncs {
		compareDict = IdentityDictionary.new;
		beaconOSCFunc = OSCFunc({|msg, time, addr|
			var name, count, numPeers;
			if(addrBook.includesMatchedAddr(addr), {
				name = msg[1];
				count = msg[2];
				numPeers = msg[3];
				if(name != addrBook.me.name || (numPeers < 3), { // ignore my own beacons if possible
					//(addrBook.me.name ++ "received Beacon").postln;
					compareDict[(name ++ count).asSymbol] = IdentityDictionary[\numPeers -> numPeers, \replies->Array.new(numPeers), \beaconTime->time];
					addrBook.sendExcluding(name, oscPath ++ '-compare', name ++ count, this.tempo, this.beats);
				});
			}, {"BeaconClock received beacon from unknown address: %\n".format(addr).warn;});
		}, oscPath);

		compareOSCFunc = OSCFunc({|msg, time, addr|
			var key, tempo, beats, replies;
			//\foo.postln;
			//msg.postln;
			key = msg[1];
			if(addrBook.includesMatchedAddr(addr), {
				if(compareDict[key].notNil, { // the second if is required at the moment to allow testing on the same machine
					tempo = msg[2];
					beats = msg[3];
					//compareDict.postcs;
					replies = compareDict[key][\replies];
					replies.add([tempo, beats]);
					//(addrBook.me.name ++ "received compare; replies: %\n").postf(replies);
					if(replies.size == compareDict[key][\numPeers], {
						this.calcTempoAndBeats(replies, compareDict[key][\beaconTime]);
						compareDict[key] = nil; // let if be GC'd
					});
				});
			}, {"BeaconClock received compare message from unknown address: %\n".format(addr).warn;});
		}, oscPath ++ '-compare');
	}

	calcTempoAndBeats {|replies, beaconTime|
		var newTempo, newBeats;
		replies = replies.flop;
		// just average for now
		// could do other things like take latest, or add
		newTempo = replies[0].mean;
		newBeats = replies[1].maxItem + (Main.elapsedTime - beaconTime * newTempo);
		//"% newTempo: % newBeats: %\n".postf(addrBook.me.name, newTempo, newBeats);
		this.tempo_(newTempo);
		this.beats_(newBeats);
	}
}