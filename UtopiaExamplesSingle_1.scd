/*

These examples are designed to run on one machine, but simulate the effect of running on two
Hopefully it is clear which code would run on each
Some functionality cannot be demonstrated this way

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

~hail = Hail(~addrBook);
)

//fake another participant
~hail2 = Hail(me: Peer(\me2, NetAddr.localAddr));

// \me2 goes offline
~hail2.free;

//cleanup
~hail.free; ~win.close;



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

~registrar = Registrar(); // this could be running on a separate computer or in a separate process
~registrant = Registrant(~addrBook);
)

//fake another participant
~registrant2 = Registrant(me: Peer(\me2, NetAddr.localAddr));

// \me2 goes offline
~registrant2.free;

//cleanup
~registrant.free; ~registrar.free; ~win.close;


////////////////////////
// Security: Using Authenticators and Encryption

// We'll use ChallengeAuthenticator, which does a simple unencrypted Challenge response test before allowing someone to join
// You could make more sophisticated authenticators if desired

// Here's a challenge. Better in real life to use a long collection, like the text of War and Peace ;-)
// Both peers know this, but it's never sent in its entirety over the network

~challenge = "The quick brown fox jumped over the lazy dog";

// We can't really test this on one machine, since a single instance both issues and answers challengers, but it would look like this:
// ~registrar = Registrar(authenticator: ChallengeAuthenticator(~challenge));
// ~registrant1 = Registrant(authenticator: ChallengeAuthenticator(~challenge));

// Another example is GroupPasswordAuthenticator, which uses a symmetric password to allow peers to join
// ~registrar = Registrar(authenticator: GroupPasswordAuthenticator("foobar"));
// ~registrant1 = Registrant(authenticator: GroupPasswordAuthenticator("foobar"));

// GroupPasswordAuthenticator uses encryption by default so that passwords are not sent in plaintext. By default this uses OpenSSLSymEncryptor
// which does symmetric key encryption of text. (Requires OpenSSL installed and in your path!)

e = OpenSSLSymEncryptor("password", "bf"); // impossible to guess password, use blowfish encryption

a = e.encryptText("Test")

e.decryptText(a); // should say test

// When used with encryption GroupPasswordAuthenticator should be safe even in decentralised registration, since observers cannot
// decrypt the responses to password challenges without the password (or alternatively an additional one).




////////////////////////
// Chatter, one of a number of classes which relay data to members of an AddrBook

// make some peers and address books
(
thisProcess.openUDPPort(3000);
~peer1 = Peer(\me1, NetAddr.localAddr);
~peer2 = Peer(\me2, NetAddr("127.0.0.1", 3000)); // I'll use a different port and simulate a different machine
~addrBook1 = AddrBook().addMe(~peer1);
~addrBook2 = AddrBook().addMe(~peer2);
~addrBook1.add(~peer2);
~addrBook2.add(~peer1);
)

~chatter1 = Chatter(~addrBook1, false);
~chatter2 = Chatter(~addrBook2);

~chatter1.send("howdy");
~chatter1.sendPrivate(\me2, "Psst... howdy!"); // send only to \me2

// make a GUI for peer1
(
~win = Window("Chatty Betty").front;
~win.layout = VLayout(~textField = TextField(), ~textView = TextView());
~textField.action_({|v| ~chatter1.send(v.string); v.string = "" });
// to get updates, just add a dependant
~chatter1.addDependant({|chatter, what, who, chat|
	{ ~textView.string_(who ++ ": " ++ chat ++ "\n" ++ ~textView.string) }.defer;
});
)

~chatter2.send("Works from me too");

// cleanup
~chatter1.free; ~chatter2.free; ~win.close;



////////////////////////
// Code Relay

// make some peers and address books
(
thisProcess.openUDPPort(3000);
~peer1 = Peer(\me1, NetAddr.localAddr);
~peer2 = Peer(\me2, NetAddr("127.0.0.1", 3000)); // I'll use a different port and simulate a different machine
~addrBook1 = AddrBook().addMe(~peer1);
~addrBook2 = AddrBook().addMe(~peer2);
~addrBook1.add(~peer2);
~addrBook2.add(~peer1);
)

// only one posts code when received
// and we'll pass a no-op codeDumpFunc to avoid duplicates sends since we're on one machine
~codeRelay2 = CodeRelay(~addrBook2, true, codeDumpFunc: {});
~codeRelay1 = CodeRelay(~addrBook1);


//some dumb code to execute
\foo.postln;
1 + 1;

// make a GUI for peer1
(
~win = Window("Codey Larry").front;
~win.layout = VLayout(~textView = TextView());
// to get updates, just add a dependant
// you could update History here for example
~codeRelay2.addDependant({|chatter, what, who, code|
	{ ~textView.string_(who ++ ":\n" ++ code ++ "\n\n" ++ ~textView.string) }.defer;
});
)

//some more dumb code to execute
\bar.postln;
1 + 1;

// cleanup
~codeRelay1.free; ~codeRelay2.free; ~win.close;



////////////////////////
// A very dumb clock example to show how you might integrate a clock with this
// Master and slave clocks must share a common time base, e.g. using the NTP class
// Ticks are scheduled with a small latency, so as to avoid drift

// make a peer and an address book
(
~peer1 = Peer(\me1, NetAddr.localAddr);
~addrBook = AddrBook();
~addrBook.add(~peer1);
)

// make some slaves first, then the master. They only advance when master ticks, so they should stay aligned
// these could be on different machines, but you could also have multiple slaves on one machine...
(
~slave1 = FollowerClock(NetAddr.localAddr);
~slave2 = FollowerClock(NetAddr.localAddr);
)
~master = ConductorClock(~addrBook); // add a master which starts ticking, this would probably be on a different machine or process

~slave1.sched(1, {\foo.postln}); SystemClock.sched(1, {\bar.postln});

Pbind(\degree, Pseq((1..4), inf)).play(~slave1, quant: 1); Pbind(\degree, Pseq((5..8), inf)).play(~slave2, quant: 1);
~master.tempo = 3;

Pbind(\degree, Pseq((2..5), inf), \dur, 3, \octave, 6).play(~slave2, quant: 1);

~slave1.beats == ~slave2.beats; // these will be the same

// cleanup
~slave1.free; ~slave2.free; ~master.stop;



////////////////////////
// Beacon Clocks

(
~peer1 = Peer(\me1, NetAddr("192.168.1.5", 57120));
~peer2 = Peer(\me2, NetAddr("192.168.1.5", 57120));
~peer3 = Peer(\me3, NetAddr("192.168.1.5", 57120));
~addrBook1 = AddrBook().addMe(~peer1);
~addrBook2 = AddrBook().addMe(~peer2);
~addrBook3 = AddrBook().addMe(~peer3);
~addrBook1.add(~peer2);
~addrBook3.add(~peer2);
~addrBook2.add(~peer1);
~addrBook3.add(~peer1);
~addrBook1.add(~peer3);
~addrBook2.add(~peer3);
)

(
~clock1 = BeaconClock(~addrBook1);
~clock2 = BeaconClock(~addrBook2);
~clock3 = BeaconClock(~addrBook3);
)

"Beats1: % Beats2: % Beats3: % Tempo1: % Tempo2: % Tempo3: %\n".postf(~clock1.beats, ~clock2.beats, ~clock3.beats, ~clock1.tempo, ~clock2.tempo, ~clock3.tempo);

(
Pbind(\degree, Pseq((1..3), inf)).play(~clock1);
Pbind(\degree, Pseq((4..6), inf)).play(~clock2);
Pbind(\degree, Pseq((8..10), inf)).play(~clock3);
)

(
~clock2.tempo = 1.1;
SystemClock.sched(0, {
"Beats1: % Beats2: % Beats3: % Tempo1: % Tempo2: % Tempo3: %\n".postf(~clock1.beats, ~clock2.beats, ~clock3.beats, ~clock1.tempo, ~clock2.tempo, ~clock3.tempo);
	0.1
})
)

~clock3.tempo = 0.1;
~clock3.tempo = 30;


////////////////////////
// Use OSCDataSpace and OSCObjectSpace, in this case to make local instances for remote Servers and share SynthDefs
// as well as to share some parameters

// make some peers and address books
(
thisProcess.openUDPPort(3000);
~peer1 = Peer(\me1, NetAddr.localAddr);
~peer2 = Peer(\me2, NetAddr("127.0.0.1", 3000)); // I'll use a different port and simulate a different machine
~addrBook1 = AddrBook().addMe(~peer1);
~addrBook2 = AddrBook().addMe(~peer2);
~addrBook1.add(~peer2);
~addrBook2.add(~peer1);
// make some client IDs; we only need this if we want to play on each other's servers
~me1ClientID = 1;
~me2ClientID = 2;
)
// here each participant creates and boots her server
(
~server1 = Server(\me1Server, NetAddr("127.0.0.1", 57111), clientID:~me1ClientID);
~server2 = Server(\me2Server, NetAddr("127.0.0.1", 57112), clientID:~me2ClientID);
~server1.boot;
~server2.boot;
)

// here we will share server addresses
// OSCObjectSpace makes local copies of archivable objects stored remotely (see notes below)
(
~serverAddrs1 = OSCObjectSpace(~addrBook1, oscPath:'/serverAddrs'); // me1's local copy
~serverAddrs2 = OSCObjectSpace(~addrBook2, oscPath:'/serverAddrs'); // me2's local copy
)

(
~me1RemoteServers = IdentityDictionary.new; // here me1 will store her remote Server objects
~serverAddrs1.addDependant({|objectSpace, what, key, val|
	var newServer;
	if(key != ~addrBook1.me.name, {
		"New Server!!".postln;
		newServer = Server(key, val, clientID:~me1ClientID);
		~me1RemoteServers[key] = newServer;
		SynthDescLib.global.addServer(newServer); // the remote server now gets any defs I add
	});
});
)

~serverAddrs1.put(\me1, ~server1.addr); // me1 adds her Server addr
~serverAddrs2.put(\me2, ~server2.addr); // me2 adds his Server addr; now me1 should have a local Server object referring to it

// we'll get updated each time a new desc is added remotely
// and SynthDescRelay adds the desc to the specified lib
(
~synthDescRel1 = SynthDescRelay(~addrBook1);
~lib2 = SynthDescLib(\lib2, ~server2); // fake a different default SynthDescLib on a different machine
~synthDescRel2 = SynthDescRelay(~addrBook2, libName:\lib2);
)
// me2 adds an action for new descs
(
~synthDescRel2.addDependant({|descRelay, what, desc, defcode|
	"Check out this new def!!\n\n%\n".format(defcode).postln;
});
)

// simulate me1 making a def
SynthDef(\foo, {|freq = 440, dur = 1, amp = 0.1| Out.ar(0, SinOsc.ar(freq, 0, amp) * Env.sine(dur).kr(2));}).add;

// here we'll share some parameters
// OSCDataSpace allows OSC types only
// NB that Strings are converted to Symbols by SC's OSC implementation
(
~params1 = OSCDataSpace(~addrBook1, '/params');
~params2 = OSCDataSpace(~addrBook2, '/params');
)

// me2 sets the freq
~params2[\freq] = 880;

// me1 starts playing on me2's server
Pbind(\instrument, \foo, \freq, Pfunc({~params2[\freq]}), \server, ~me1RemoteServers[\me2]).play;

// me2 sets the freq which changes me1's stream
~params2[\freq] = 660;

// cleanup
(
~server1.quit;
~server2.quit;
~serverAddrs1.free;
~serverAddrs2.free;
~synthDescRel1.free;
~synthDescRel2.free;
~params1.free;
~params2.free;
)

/* Notes about OSCObjectSpace

1. Only objects which can be archived (i.e. those that do not contain open functions) can be sent
2. OSCObjectSpace does not track internal changes in the objects it holds. You can set the key again with the changed object, but this will result in a new object being made in other peer's object spaces, not the old one being changed. If you want to sync changes to remote objects the OpenObject quark might be better.
3. OSCObjectSpace can constitute a security risk, since a malicious party could insert a pseudo-object spoofing something else. For this reason, by default it does not accept Events, although you can enable that. For safety it is best to use it on a secure network, and with encryption. If auto-discovering peers on an open network, the use of an autheniticator such as ChallengeAuthenticator can provide additional security.

*/