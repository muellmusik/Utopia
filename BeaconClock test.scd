~clock = BeaconClock(~addrBook);

Pbind(\freq, rrand(440, 880)).play(~clock, quant: 1);

Pbind(\freq, Pfunc({"beats: % tempo: %\n".postf(~clock.beats, ~clock.tempo); rrand(440, 1200) })).play(~clock, quant: 1);

~clock.tempo = 11

(
SynthDef(\click, {
	var click;
	click = Impulse.ar(0);
	DetectSilence.ar(click,doneAction:2);
	OffsetOut.ar(0, click * 0.5);
}).add;
)

Pbind(\instrument, \click, \freq, Pfunc({"beats: % tempo: %\n".postf(~clock.beats, ~clock.tempo); rrand(440, 1200) })).play(~clock, quant: 1);