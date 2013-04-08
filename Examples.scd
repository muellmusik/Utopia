////////////////////////
// decentralised discovery of participants
// find who's on the network
(
~win = Window("AdHocSociety").front;
~win.layout = VLayout.new.add(~listView = ListView.new);

~addrBook = AddrBook.new;

~addrBook.addDependant({|addrBook, what, who|
	{~listView.items = addrBook.citizens.collectAs({|cit|
		cit.name ++ " | " ++ cit.addr.ip ++ " | " ++ if(cit.online, "online", "offline");
	}, Array)}.defer;
});

~addrBook.addMe;

~attendance = Attendance(~addrBook);
)

//fake another participant
~attendance2 = Attendance(me: OSCitizen(\me2, NetAddr.localAddr, s.addr));

// \me2 goes offline
~attendance2.free;

//cleanup
~attendance.free; ~win.close;



////////////////////////
// Equivalent example using Registrar (centralised registration)
(
~win = Window("Benevolent Dictatorship").front;
~win.layout = VLayout.new.add(~listView = ListView.new);

~addrBook = AddrBook.new;

~addrBook.addDependant({|addrBook, what, who|
	{~listView.items = addrBook.citizens.collectAs({|cit|
		cit.name ++ " | " ++ cit.addr.ip ++ " | " ++ if(cit.online, "online", "offline");
	}, Array)}.defer;
});

~addrBook.addMe;

~registrar = Registrar(); // this could be running on a separate computer or in a separate process
~registrant = Registrant(~addrBook);
)

//fake another participant
~registrant2 = Registrant(me: OSCitizen(\me2, NetAddr.localAddr, s.addr));

// \me2 goes offline
~registrant2.free;

//cleanup
~registrant.free; ~registrar.free; ~win.close;