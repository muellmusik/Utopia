// really very dumb master and slave clocks
// mostly for demonstration purposes
// these assume you have a shared timebase, e.g. via NTP

SlaveClock : Clock {
	var masterAddr, oscPath, scheduler, tickOSCFunc, <tempo = 1;

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
		this.schedAbs(quant.nextTimeOnGrid(this), task)
	}

	beatDur {
		^1/tempo;
	}
	beats {
		^scheduler.seconds;
	}
	nextTimeOnGrid {|quant= 1, phase= 0|
		var beats;
		beats = this.beats;
		if(quant.isNumber.not, {
			quant= quant.quant;
		});
		if(quant==0, {^beats+(phase*24)});
		if(beats==0, {^phase*24});
		^beats+((24*quant)-(beats%(24*quant)))+(phase%quant*24);
	}
}

MasterClock {
	var addrBook, latency, granularity, oscPath, tempoClock;

	*new { |addrBook, latency = 0.1, granularity = 0.005, oscPath = '/masterClockTick', tempo, beats, seconds, queueSize=256|
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