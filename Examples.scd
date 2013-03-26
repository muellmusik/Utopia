// find who's on the network
(
~win = Window("AdHocSociety").front;
~win.layout = VLayout.new.add(~listView = ListView.new);

~addrBook = AddrBook.new.addMe;

~addrBook.addDependant({|addrBook, what, who|
	{~listView.items = addrBook.citizens.collectAs({|cit|
		cit.name ++ " | " ++ cit.addr.ip ++ " | " ++ if(cit.online, "online", "offline");
	}, Array)}.defer;
});

~attendance = Attendance(~addrBook);
)

//fake another participant
~attendance2 = Attendance(me: OSCitizen(\me2, NetAddr.localAddr, s.addr));

// \me2 goes offline
~attendance2.free;