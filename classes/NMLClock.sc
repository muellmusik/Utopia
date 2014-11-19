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
/// However, works better with at least three participants, as then you don't need
/// to sync to your own Beacons

/// this needs to keep track of which is last beacon
// should this have different behaviour when 'synced' or 'not synced'

// if we change the tempo in the middle of a compare, we should discard it.
// Could we have one central Beacon for all clocks to follow?

// instead of counting replies, we could include a list of names to expect a replie from

BeaconClock : TempoClock {
	var addrBook, beaconOSCFunc, compareOSCFunc, tempoOSCFunc, oscPath, compareDict, broadcastAddr;
	var fadeTask, fading=false;

	*new { |addrBook, tempo, beats, seconds, queueSize=256, oscPath = '/beaconClock'|
		if(addrBook.isNil, { "BeaconClock cannot work with nil AddrBook!".throw });
		^super.new(tempo, beats, seconds, queueSize).setVars(addrBook, oscPath).makeOSCFuncs.startBeacons;
	}

	setVars {|argAddrBook, argOSCPath| addrBook = argAddrBook; oscPath = argOSCPath; }

	startBeacons {
		var count = 0, myName, numReplies;
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

	makeOSCFuncs {
		compareDict = IdentityDictionary.new;

		// time here is elapsedTime when the processOSCPacket is called
		// since if may need to wait on the lang mutex this could be late in 3.6
		// tried to fix this in 3.7, or at least remove the lang contention
		// beats is logical time
		// so when the OSCFunc is called we first check the difference between
		// received time and now, and then recalc our beats for then

		beaconOSCFunc = OSCFunc({|msg, time, addr|
			var name, count, numReplies, myBeats;
			if(addrBook.addrs.includesEqual(addr), {
				name = msg[1];
				count = msg[2];
				numReplies = msg[3];
				if(name != addrBook.me.name || (numReplies < 3 && (numReplies > 0)), { // ignore my own beacons if possible
					//(addrBook.me.name ++ "received Beacon").postln;
					myBeats = this.secs2beats(time);
					compareDict[(name ++ count).asSymbol] = IdentityDictionary[\numReplies -> numReplies, \replies->Array.new(numReplies - 1), \beaconTime->time, \myBeats->myBeats, \myTempo->this.tempo ];

					// !!!! this needs to not send to me in most cases
					addrBook.sendExcluding(name, oscPath ++ '-compare', name ++ count, this.tempo, myBeats);
				});
			}, {"BeaconClock received beacon from unknown address: %\n".format(addr).warn;});
		}, oscPath);

		compareOSCFunc = OSCFunc({|msg, time, addr|
			var key, tempo, beats, replies;
			//\foo.postln;
			//msg.postln;
			key = msg[1];
			if(addrBook.addrs.includesEqual(addr), {
				if(compareDict[key].notNil, { // the second if is required at the moment to allow testing on the same machine
					tempo = msg[2];
					beats = msg[3];
					//compareDict.postcs;
					replies = compareDict[key][\replies];
					replies.add([tempo, beats]);
					//(addrBook.me.name ++ "received compare; replies: %\n").postf(replies);
					if(replies.size == compareDict[key][\numReplies], {
						this.calcTempoAndBeats(compareDict[key]);
						compareDict[key] = nil; // let if be GC'd
					});
				});
			}, {"BeaconClock received compare message from unknown address: %\n".format(addr).warn;});
		}, oscPath ++ '-compare');

		tempoOSCFunc = OSCFunc({|msg, time, addr|
			var tempo, beats, replies;
			tempo = msg[1];
			beats = msg[2];
			//"tempo: % beat: %\n".postf(tempo, beats);
			if(addrBook.addrs.includesEqual(addr), {
				// if the message is late, do it *now*
				if(beats < this.beats, {
					this.tempo = tempo
				}, {
					this.schedAbs(beats, {this.tempo = tempo});
				});
			}, {"BeaconClock received global tempo message from unknown address: %\n".format(addr).warn;});
		}, oscPath ++ '-globalTempo');
	}

	setGlobalTempo {|tempo, beat|
		beat = beat ?? { this.nextTimeOnGrid };
		broadcastAddr.sendMsg(oscPath ++ '-globalTempo', tempo, beat);
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
		this.tempo_(newTempo);
		// add any difference to current logical time
		this.beats_(newBeats - dict[\myBeats] + this.beats);
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
		super.permanent_(bool);
	}
}