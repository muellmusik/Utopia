



s.boot;


~addrBook = AddrBook.new;
~hail = Hail(~addrBook);
a = NMLNetRecord.new(~addrBook, s);


{ SinOscFB.ar(Rand(400.0, 300), Line.kr(1, 3, 10, doneAction:2)) * 0.1 }.play;
a.start;

// hit cmd-period, wait a little, then:

{ SinOscFB.ar(Rand(400.0, 3000), Line.kr(3, 1, 10, doneAction:2)) * 0.1 }.play;

a.stop;





// dev notes
// maybe better use:
RecNodeProxy


s.recordNode;

recordingOngoing


s.hasShmInterface

plotNodeTree

s.recordBuf