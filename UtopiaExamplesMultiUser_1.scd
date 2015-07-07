/*

Examples from Utopia/Examples - written to demonstrate on one machine,
should be much simpler when rewritten for multiple laptops.

When in a setup with multiple laptops in the same network, all running
SuperCollider and the Utopia Quark, these examples should allow testing
and demonstrating Utopia modules.

*/

////////////////////////
// decentralised discovery of participants
// find who's on the network
(
~win = Window("AdHocSociety").front;
~win.layout = VLayout.new.add(~listView = ListView.new);

~addrBook = AddrBook.new;
// to get updates, just add a dependant
~addrBook.addDependant({|addrBook, what, who|
	{~listView.items = addrBook.peers.collectAs({|peer|
		peer.name ++ " | " ++ peer.addr.ip ++ " | " ++ if(peer.online, "online", "offline");
	}, Array)}.defer;
});

~addrBook.addMe; // will automatically add you using your user name
//~addrBook.addMe(\adcxyz); // or specify one

~hail = Hail(~addrBook);

)

~addrBook.names;

////////////////////////
// Equivalent example using Registrar (centralised registration)
(
~win = Window("Benevolent Dictatorship").front;
~win.layout = VLayout.new.add(~listView = ListView.new);

~addrBook = AddrBook.new;
// to get updates, just add a dependant
~addrBook.addDependant({|addrBook, what, who|
	{~listView.items = addrBook.peers.collectAs({|peer|
		peer.name ++ " | " ++ peer.addr.ip ++ " | " ++ if(peer.online, "online", "offline");
	}, Array)}.defer;
});

~addrBook.addMe;
)

~registrar = Registrar(~addrBook); // execute this only on the central machine

~registrant = Registrant(~addrBook); // execute this on all clients


//cleanup
~registrant.free; ~registrar.free; ~win.close;


////////////////////////
// Chatter, one of a number of classes which relay data to members of an AddrBook

~chatter = Chatter(~addrBook);

~chatter.send("howdy");
~chatter.sendPrivate(\mac, "Psst... howdy!"); // send only to \mac

// make a GUI to see the results
(
~win = Window("Chatty Betty").front;
~win.layout = VLayout(~textField = TextField(), ~textView = TextView());
~textField.action_({|v| ~chatter.send(v.string); v.string = "" });
// to get updates, just add a dependant
~chatter.addDependant({|chatter, what, who, chat|
	{ ~textView.string_(who ++ ": " ++ chat ++ "\n" ++ ~textView.string) }.defer;
});
)

// cleanup
~chatter.free; ~win.close;



////////////////////////

////////////////////////
// Code Relay


~codeRelay = CodeRelay(~addrBook);
~codeRelay.post = true;

//some dumb code to execute
\foo.postln;
1 + 1;

1 + eguyetwur; // code which doesn't work won't get sent, so others don't see your mistakes!

// make a GUI to see the results
(
~win = Window("Codey Larry").front;
~win.layout = VLayout(~textView = TextView());

// to get updates, just add a dependant
// you could update History here for example
~codeRelay.addDependant({|chatter, what, who, code|
	{ ~textView.string_(who ++ ":\n" ++ code ++ "\n\n" ++ ~textView.string) }.defer;
});
)

//some more dumb code to execute, see how it shows up?
\bar.postln;
1 + 1;

// cleanup
~codeRelay.free; ~win.close;



////////////////////////
// A very dumb clock example to show how you might integrate a clock with this
// Master and slave clocks must share a common time base, e.g. using the NTP class
// Ticks are scheduled with a small latency, so as to avoid drift


// make some slaves first, then the master. They only advance when master ticks, so they should stay aligned
// these could be on different machines, but you could also have multiple slaves on one machine...
(
~slave1 = FollowerClock(); // we could pass a Peer here to allow only a single address
~slave2 = FollowerClock();
)

// add a master which starts ticking, this would probably be on a different machine or process
~master = ConductorClock(~addrBook);

~slave1.sched(1, {\foo.postln}); SystemClock.sched(1, {\bar.postln});

a = Pbind(\degree, Pseq((1..4), inf)).play(~slave1, quant: 1); b = Pbind(\degree, Pseq((5..8), inf)).play(~slave2, quant: 1);

// master change your tempo
~master.tempo = 3;

c = Pbind(\degree, Pseq((2..5), inf), \dur, 3, \octave, 6).play(~slave2, quant: 1);

~slave1.beats == ~slave2.beats; // these will be the same

// cleanup
~slave1.free; ~slave2.free; ~master.stop; a.stop; b.stop; c.stop;



////////////////////////
// Beacon Clocks

// this is a decentralised clock, we can have as many as we like on any machines
// we'll make these all the same, but you can have independent ones by specifying different
// OSC paths for them
(
~clock1 = BeaconClock(~addrBook);
~clock2 = BeaconClock(~addrBook);
~clock3 = BeaconClock(~addrBook);
)


// beats and tempi will all be the same
// get your friends to compare on their computers
"Beats1: % Beats2: % Beats3: % Tempo1: % Tempo2: % Tempo3: %\n".postf(~clock1.beats, ~clock2.beats, ~clock3.beats, ~clock1.tempo, ~clock2.tempo, ~clock3.tempo);

// somebody play something
(
a = Pbind(\degree, Pseq((1..3), inf)).play(~clock1);
b = Pbind(\degree, Pseq((4..6), inf)).play(~clock2);
c = Pbind(\degree, Pseq((8..10), inf)).play(~clock3);
)

// now somebody else try these
~clock3.setGlobalTempo(0.1);
~clock2.setGlobalTempo(30, ~clock2.beats + 0.25); // default is change on next whole beat, but we can specify

// accel or decel is also possible
~clock1.fadeTempo(1, 50); // change to 1 beat/sec over 50 beats

////////////////////////
// Use OSCDataSpace and OSCObjectSpace, in this case to make local instances for remote Servers and share SynthDefs
// as well as to share some parameters


// here we will share some arbitrary objects
// OSCObjectSpace makes local copies of archivable objects stored remotely (see notes below)
~objSpace = OSCObjectSpace(~addrBook, oscPath:'/someObjs');


// post a message when a new object comes
// you could use this to update other objects, etc.
(
~objSpace.addDependant({|objectSpace, what, key, val|
	"New object for key %: %\n".postf(key, val);
});
)

// now add some stuff
~objSpace[\point] = 2@3;

// your friends should see it
~objSpace[\point]

// somebody change it
~objSpace[\point] = ">>>>>>>";

// here we'll share some parameters
// OSCDataSpace allows OSC types only
// NB that Strings are converted to Symbols by SC's OSC implementation
~params = OSCDataSpace(~addrBook, '/params');


// now somebody set freq
~params[\freq] = 880;

// somebody else start playing
~pbind = Pbind(\freq, Pfunc({~params[\freq]})).play;

// somebody else change it
~params[\freq] = 660;

~pbind.stop;

/* Notes about OSCObjectSpace

1. Only objects which can be archived (i.e. those that do not contain open functions) can be sent
2. OSCObjectSpace does not track internal changes in the objects it holds. You can set the key again with the changed object, but this will result in a new object being made in other peer's object spaces, not the old one being changed. If you want to sync changes to remote objects the OpenObject quark might be better.
3. OSCObjectSpace can constitute a security risk, since a malicious party could insert a pseudo-object spoofing something else. For this reason, by default it does not accept Events, although you can enable that. For safety it is best to use it on a secure network, and with encryption. If auto-discovering peers on an open network, the use of an autheniticator such as ChallengeAuthenticator can provide additional security.

*/
