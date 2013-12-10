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

~addrBook.addMe(\adcxyz); // will automatically add you using your user name

~hail = Hail(~addrBook);

)

~addrBook.names;


////////////////////////
// Chatter, one of a number of classes which relay data to members of an AddrBook

~chatter = Chatter(~addrBook);

~chatter.send("howdy");
~chatter.sendPrivate(\mac, "Psst... howdy!"); // send only to \me2

// make a GUI for peer1
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

1 + eguyetwur;

// make a GUI for peer1
(
~win = Window("Codey Larry").front;
~win.layout = VLayout(~textView = TextView());

// to get updates, just add a dependant
// you could update History here for example
~codeRelay.addDependant({|chatter, what, who, code|
	{ ~textView.string_(who ++ ":\n" ++ code ++ "\n\n" ++ ~textView.string) }.defer;
});
)

//some more dumb code to execute
\bar.postln;
1 + 1;

// cleanup
~codeRelay.free; ~win.close;

