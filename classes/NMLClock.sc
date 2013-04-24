// really very dumb master and slave clocks
// mostly for demonstration purposes
// these assume you have a shared timebase, e.g. via NTP

FollowerClock : Clock {
	var masterAddr, oscPath, scheduler, tickOSCFunc, <tempo = 1;
	var <beatsPerBar=4.0, barsPerBeat=0.25;
	var <baseBarBeat=0.0, <baseBar=0.0;

	*new {|masterAddr, oscPath = '/masterClockTick'|
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

	*new { |addrBook, latency = 0.05, granularity = 0.01, oscPath = '/masterClockTick', tempo, beats, seconds, queueSize=256|
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