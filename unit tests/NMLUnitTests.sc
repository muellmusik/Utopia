// tests locally...
TestAttendance : UnitTest {
	var citizen1, addrBook1, attendance1;
	var citizen2, addrBook2, attendance2;

	setUp {
		citizen1 = OSCitizen(\cit1, NetAddr.localAddr, Server.default.addr);
		citizen2 = OSCitizen(\cit2, NetAddr.localAddr, Server.default.addr);

		addrBook1 = AddrBook.new.addMe(citizen1);
		addrBook2 = AddrBook.new.addMe(citizen2);
	}

	tearDown {
		attendance1.free;
		attendance2.free;
	}

	test_takeAttendance {
		var period = 1;
		attendance1 = Attendance(addrBook1, period);
		attendance2 = Attendance(addrBook2, period);

		(period * 1.5).wait;

		this.assert(addrBook1[\cit1] == addrBook2[\cit1], "% in both books".format(citizen1));
		this.assert(addrBook1[\cit2] == addrBook2[\cit2], "% in both books".format(citizen2));

		(period * 1.5).wait;

		this.assert(addrBook1[\cit2].online, "citizen2 shows online");
		this.assert(addrBook2[\cit1].online, "citizen1 shows online");
	}
}

TestRegistrar : UnitTest {
	var citizen1, addrBook1, registrant1;
	var citizen2, addrBook2, registrant2;
	var registrar;

	setUp {
		citizen1 = OSCitizen(\cit1, NetAddr.localAddr, Server.default.addr);
		citizen2 = OSCitizen(\cit2, NetAddr.localAddr, Server.default.addr);

		addrBook1 = AddrBook.new.addMe(citizen1);
		addrBook2 = AddrBook.new.addMe(citizen2);
	}

	tearDown {
		registrant1.free;
		registrant2.free;
		registrar.free;
	}

	test_takeRegister {
		var period = 1;
		registrar = Registrar(period: period);
		registrant1 = Registrant(addrBook1);
		registrant2 = Registrant(addrBook2);

		(period * 1.5).wait;

		this.assert(addrBook1[\cit1] == addrBook2[\cit1], "% in both books".format(citizen1));
		this.assert(addrBook1[\cit2] == addrBook2[\cit2], "% in both books".format(citizen2));

		(period * 1.5).wait;

		this.assert(addrBook1[\cit2].online, "citizen2 shows online");
		this.assert(addrBook2[\cit1].online, "citizen1 shows online");

		registrant1.unregister;
		(period * 1.5).wait;

		this.assert(addrBook2[\cit1].isNil, "citizen1 unregistered successfully");
	}
}

TestChatter : UnitTest {
	var citizen1, addrBook1, chatter1;
	var citizen2, addrBook2, chatter2;

	setUp {
		var port2;
		port2 = NetAddr.langPort + 1;
		while({thisProcess.openUDPPort(port2).not}, {port2 = port2 + 1});
		citizen1 = OSCitizen(\cit1, NetAddr.localAddr, Server.default.addr);
		citizen2 = OSCitizen(\cit2, NetAddr.localAddr.port_(port2), Server.default.addr);

		addrBook1 = AddrBook.new.addMe(citizen1).add(citizen2);
		addrBook2 = AddrBook.new.addMe(citizen2).add(citizen1);
	}

	tearDown {
		chatter1.free;
		chatter2.free;
	}

	test_idleChatter {
		var period = 1, messageReceived, receivedFrom;
		chatter1 = Chatter(addrBook1);
		chatter2 = Chatter(addrBook2, false);

		chatter1.addDependant({|what, who, chat| receivedFrom = who; messageReceived = chat; });

		"Testing global chat:".postln;
		chatter2.send("Hello Net!");

		this.asynchAssert({messageReceived.notNil}, {messageReceived == "Hello Net!" && receivedFrom == \cit2}, "chat not received", 2);

		messageReceived = nil; receivedFrom = nil;
		"Testing private chat:".postln;
		chatter2.sendPrivate(\cit1, "Psst");

		this.asynchAssert({messageReceived.notNil}, {messageReceived == "Psst" && receivedFrom == \cit2}, "chat not received", 2);

	}
}