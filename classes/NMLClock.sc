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

	permanent_ {|bool| tempoClock.permanent_(bool); }

	stop { tempoClock.permanent = false; tempoClock.stop; }

}

/// Pseudo Reference Broadcast Synchronisation Clock
/// These do not require a common timebase, and are self converging
/// However, works better with at least three participants, as then you don't need
/// to sync to your own Beacons

/// this needs to keep track of which is last beacon
// should this have different behaviour when 'synced' or 'not synced'

// if we change the tempo in the middle of a compare, we should discard it.
// Could we have one central Beacon for all clocks to follow?

// instead of counting replies, we could include a list of names to expect a replie from

BeaconClock : TempoClock {
	var addrBook, beaconOSCFunc, compareOSCFunc, tempoOSCFunc, clearOSCFunc;
	var oscPath, compareDict, broadcastAddr;
	var compareOSCpath, globalTempoOSCpath, globalClearOSCpath;
	var fadeTask, fading=false;
	var count = 0;

	*new { |addrBook, tempo, beats, seconds, queueSize=256, oscPath = '/beaconClock'|
		if(addrBook.isNil, { "BeaconClock cannot work with nil AddrBook!".throw });
		^super.new(tempo, beats, seconds, queueSize).setVars(addrBook, oscPath).makeOSCFuncs.startBeacons;
	}

	setVars {|argAddrBook, argOSCPath|
		addrBook = argAddrBook;
		oscPath = argOSCPath;
		compareOSCpath = (oscPath ++ '-compare').asSymbol;
		globalTempoOSCpath = (oscPath ++ '-globalTempo').asSymbol;
		globalClearOSCpath = (oscPath ++ '-globalClear').asSymbol;
	}

	startBeacons {
		var myName, numReplies;
		// unusually we'll use broadcast here to avoid variations in send time
		NetAddr.broadcastFlag = true;
		broadcastAddr = NMLNetAddrMP("255.255.255.255", 57120 + (0..7));
		myName = addrBook.me.name;
		SystemClock.sched(rrand(0, 0.1) * addrBook.onlinePeers.size, { // what clock should this be on? Should it be permanent?
			//(myName ++ "sending Beacon").postln;
			// number of replies each Peer receiving the Beacon should expect
			numReplies = addrBook.onlinePeers.size;
			// only listen to my own beacons if I need to
			if(numReplies > 2, {numReplies = numReplies -1});
			broadcastAddr.sendMsg(oscPath, myName, count, numReplies);
			count = count + 1;
			(rrand(0.1, 0.2) * max(addrBook.onlinePeers.size, 1)); // minimum wait even if no other peers
		});
	}

	cmdPeriod { this.startBeacons }

	makeOSCFuncs {
		compareDict = IdentityDictionary.new;

		// time here is elapsedTime when the processOSCPacket is called
		// since if may need to wait on the lang mutex this could be late in 3.6
		// tried to fix this in 3.7, or at least remove the lang contention
		// beats is logical time
		// so when the OSCFunc is called we first check the difference between
		// received time and now, and then recalc our beats for then

		beaconOSCFunc = OSCFunc({|msg, time, addr|
			var name, count, numReplies, myBeats, beaconKey, onlinePeers;
			if(addrBook.includesAddr(addr) || {NetAddr.localAddr.matches(addr)}, {
				name = msg[1];
				count = msg[2];
				numReplies = msg[3];
				onlinePeers = addrBook.onlinePeers;
				if((name != addrBook.me.name && numReplies == onlinePeers.size) || (numReplies < 3 && (numReplies > 0) && numReplies == onlinePeers.size), { // ignore my own beacons if possible
					//(addrBook.me.name ++ "received Beacon").postln;
					beaconKey = (name ++ count).asSymbol;
					myBeats = this.secs2beats(time);
					compareDict.clear; // we will only correct for latest beacon
					compareDict.put(\beaconKey, beaconKey);
					compareDict.put(\numReplies, numReplies);
					compareDict.put(\replies, Array.new(numReplies - 1));
					compareDict.put(\beaconTime, time);
					compareDict.put(\myBeats, myBeats);
					compareDict.put(\myTempo, this.tempo);
					compareDict.put(\compareAddrs, onlinePeers.collect({|peer| peer.addr}));

					// !!!! this needs to not send to me in most cases
					addrBook.sendExcluding(name, compareOSCpath, beaconKey, this.tempo, myBeats);
				});
			}, {"BeaconClock received beacon from unknown address: %\n".format(addr).warn;});
		}, oscPath);

		compareOSCFunc = OSCFunc({|msg, time, addr|
			var key, tempo, beats, replies;
			//\foo.postln;
			//msg.postln;
			key = msg[1];
			if(addrBook.includesAddr(addr), {
				if(compareDict[\beaconKey] == key && {compareDict[\compareAddrs].includesEqual(addr)}, {
					tempo = msg[2];
					beats = msg[3];
					//compareDict.postcs;
					replies = compareDict[\replies];
					replies.add([tempo, beats]);
					//(addrBook.me.name ++ "received compare; replies: %\n").postf(replies);
					if(replies.size == compareDict[\numReplies], {
						this.calcTempoAndBeats(compareDict);
					});
				});
			}, {"BeaconClock received compare message from unknown address: %\n".format(addr).warn;});
		}, compareOSCpath);

		tempoOSCFunc = OSCFunc({|msg, time, addr|
			var tempo, beats, replies;
			tempo = msg[1];
			beats = msg[2];
			//"tempo: % beat: %\n".postf(tempo, beats);
			if(addrBook.includesAddr(addr), {
				// if the message is late, do it *now*
				if(beats < this.beats, {
					this.tempo = tempo
				}, {
					this.schedAbs(beats, {this.tempo = tempo});
				});
			}, {"BeaconClock received global tempo message from unknown address: %\n".format(addr).warn;});
		}, globalTempoOSCpath);

		clearOSCFunc = OSCFunc({|msg, time, addr|
			var releaseNodes;
			releaseNodes = msg[1].booleanValue;
			if(addrBook.includesAddr(addr), {
				this.clear(releaseNodes);
			}, {"BeaconClock received global clear message from unknown address: %\n".format(addr).warn;});
		}, globalClearOSCpath);
	}

	setGlobalTempo {|tempo, beat|
		beat = beat ?? { this.nextTimeOnGrid };
		broadcastAddr.sendMsg(globalTempoOSCpath, tempo, beat);
	}

	globalClear {|releaseNodes = true|
		broadcastAddr.sendMsg(globalClearOSCpath, releaseNodes.binaryValue);
	}

	tempo_ {|newTempo|
		// clear any compares in progress
		compareDict.clear;
		super.tempo_(newTempo);
	}

	// so actually here we need to just compare the difference between our old beats
	// and the 'correct' beats at beacon time and add that
	// if we discard any compares that were incomplete when a tempo change occurred
	// this should be sufficient
	calcTempoAndBeats {|dict|
		var replies, beaconTime;
		var newTempo, newBeats, diff;
		replies = dict[\replies];
		beaconTime = dict[\beaconTime];
		replies = replies.flop;
		// just average for now
		// could do other things like take latest, or add
		newTempo = replies[0].mean;
		newBeats = replies[1].maxItem;
		//"% newTempo: % newBeats: %\n".postf(addrBook.me.name, newTempo, newBeats);
		// add any difference to current logical time
		diff = newBeats - dict[\myBeats];
		this.beats_(diff + this.beats);
		this.tempo_(newTempo); // this also clears the dict
	}

	// fade and warp tempo from the virtual gamelan project's SoftClock
	// currently this is 'precise' but with latency,
	// i.e. we set all Clock's tempi at dt in the
	// future, so they should be as together as anything else it does
	fadeTempo { arg newTempo, dur = 1.0, warp = \cos, clock, dt = 0.1, verbose = false;
		var start = this.tempo, interpol;
		warp = warp.asWarp;
		if (warp.isKindOf(ExponentialWarp)) { warp.spec.minval_(0.01) };
		if (fading) { fadeTask.stop };
		fadeTask = Task {
			fading = true;
			"fadeTempo starts. going from: % to: %\n".postf(
				start.round(0.001), newTempo.round(0.001));
			(1 .. (dur / dt + 1).asInteger).normalize.do { |val|
				interpol = blend(start, newTempo, warp.map(val));
				this.setGlobalTempo(interpol, dt);
				if (verbose) { "fadeTempo index: % tempo: %\n".postf(
					val.round(0.001), interpol.round(0.001)) };
				dt.value.wait;
			};
			fading = false;
			"fadeTempo done. tempo was: % new tempo is: %\n".postf(
				start.round(0.001), interpol.round(0.001));
		};
		clock = clock ? SystemClock;
		fadeTask.play(clock);
	}

	warpTempo { arg frac, beats = 1.0, warp = \cos;
		this.fadeTempo(frac * this.tempo, beats, warp, this)
	}

	permanent_{|bool|
		beaconOSCFunc.permanent_(bool);
		compareOSCFunc.permanent_(bool);
		tempoOSCFunc.permanent_(bool);
		clearOSCFunc.permanent_(bool);
		if(bool, {CmdPeriod.add(this)}, {CmdPeriod.remove(this)});
		super.permanent_(bool);
	}
}