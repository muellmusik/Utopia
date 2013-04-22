// tests locally...
TestHail : UnitTest {
	var peer1, addrBook1, hail1;
	var peer2, addrBook2, hail2;

	setUp {
		peer1 = Peer(\peer1, NetAddr.localAddr);
		peer2 = Peer(\peer2, NetAddr.localAddr);

		addrBook1 = AddrBook.new.addMe(peer1);
		addrBook2 = AddrBook.new.addMe(peer2);
	}

	tearDown {
		hail1.free;
		hail2.free;
	}

	test_hailingSignal {
		var period = 1;
		hail1 = Hail(addrBook1, period);
		hail2 = Hail(addrBook2, period);

		(period * 1.5).wait;

		this.assert(addrBook1[\peer1] == addrBook2[\peer1], "% in both books".format(peer1));
		this.assert(addrBook1[\peer2] == addrBook2[\peer2], "% in both books".format(peer2));

		(period * 1.5).wait;

		this.assert(addrBook1[\peer2].online, "peer2 shows online");
		this.assert(addrBook2[\peer1].online, "peer1 shows online");
	}
}

TestRegistrar : UnitTest {
	var peer1, addrBook1, registrant1;
	var peer2, addrBook2, registrant2;
	var registrar;

	setUp {
		peer1 = Peer(\peer1, NetAddr.localAddr);
		peer2 = Peer(\peer2, NetAddr.localAddr);

		addrBook1 = AddrBook.new.addMe(peer1);
		addrBook2 = AddrBook.new.addMe(peer2);
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

		this.assert(addrBook1[\peer1] == addrBook2[\peer1], "% in both books".format(peer1));
		this.assert(addrBook1[\peer2] == addrBook2[\peer2], "% in both books".format(peer2));

		(period * 1.5).wait;

		this.assert(addrBook1[\peer2].online, "peer2 shows online");
		this.assert(addrBook2[\peer1].online, "peer1 shows online");

		registrant1.unregister;
		(period * 1.5).wait;

		this.assert(addrBook2[\peer1].isNil, "peer1 unregistered successfully");
	}
}

TestChatter : UnitTest {
	var peer1, addrBook1, chatter1;
	var peer2, addrBook2, chatter2;

	setUp {
		var port2;
		port2 = NetAddr.langPort + 1;
		while({thisProcess.openUDPPort(port2).not}, {port2 = port2 + 1});
		peer1 = Peer(\peer1, NetAddr.localAddr);
		peer2 = Peer(\peer2, NetAddr.localAddr.port_(port2));

		addrBook1 = AddrBook.new.addMe(peer1).add(peer2);
		addrBook2 = AddrBook.new.addMe(peer2).add(peer1);
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

		this.asynchAssert({messageReceived.notNil}, {messageReceived == "Hello Net!" && receivedFrom == \peer2}, "chat not received", 2);

		messageReceived = nil; receivedFrom = nil;
		"Testing private chat:".postln;
		chatter2.sendPrivate(\peer1, "Psst");

		this.asynchAssert({messageReceived.notNil}, {messageReceived == "Psst" && receivedFrom == \peer2}, "chat not received", 2);

	}
}

TestChallengeAuthenticator : UnitTest {
	var peer1, challenge1;
	var challengeColl1, challengeColl2;

	setUp {
		peer1 = Peer(\peer1, NetAddr.localAddr);
		challengeColl1 = Array.fill(1000, { 1.0.rand });
		challengeColl2 = "The quick brown fox";
	}

	tearDown {
		challenge1.free;
	}

	test_authentication {
		var result;
		challenge1 = ChallengeAuthenticator(challengeColl1); // this will respond to itself

		"Testing challenge authentication with Array of floats as challenge:".postln;
		challenge1.authenticate(peer1, {result = true });

		this.asynchAssert({result.notNil}, {result}, "authentication timed out", 2);

		result = nil;
		challenge1.free;
		challenge1 = ChallengeAuthenticator(challengeColl2); // this will respond to itself

		"Testing challenge authentication with String as challenge:".postln;
		challenge1.authenticate(peer1, {result = true });

		this.asynchAssert({result.notNil}, {result}, "authentication timed out", 2);
	}
}

TestOSCDataSpace : UnitTest {
	var peer1, addrBook1, dataSpace1;
	var peer2, addrBook2, dataSpace2;

	setUp {
		var port2;
		port2 = NetAddr.langPort + 1;
		while({thisProcess.openUDPPort(port2).not}, {port2 = port2 + 1});
		peer1 = Peer(\peer1, NetAddr.localAddr);
		peer2 = Peer(\peer2, NetAddr.localAddr.port_(port2));

		addrBook1 = AddrBook.new.addMe(peer1).add(peer2);
		addrBook2 = AddrBook.new.addMe(peer2).add(peer1);
	}

	tearDown {
		dataSpace1.free;
		dataSpace2.free;
	}

	test_dataSpace {
		dataSpace1 = OSCDataSpace(addrBook1);
		dataSpace1.put(\int, 1);

		1.0.wait;
		"Testing dataSpaceSync:".postln;
		dataSpace2 = OSCDataSpace(addrBook2);
		dataSpace2.sync;

		this.asynchAssert({dataSpace2[\int].notNil}, {dataSpace1[\int] == dataSpace2[\int]}, "dataspace sync failed", 2);

		"Testing dataSpacePut:".postln;
		dataSpace2[\float] = 2.0;

		this.asynchAssert({dataSpace1[\float].notNil}, {dataSpace1[\float] == dataSpace2[\float]}, "dataspace put failed", 2);

	}
}

TestOSCObjectSpace : UnitTest {
	var peer1, addrBook1, objectSpace1;
	var peer2, addrBook2, objectSpace2;

	setUp {
		var port2;
		port2 = NetAddr.langPort + 1;
		while({thisProcess.openUDPPort(port2).not}, {port2 = port2 + 1});
		peer1 = Peer(\peer1, NetAddr.localAddr);
		peer2 = Peer(\peer2, NetAddr.localAddr.port_(port2));

		addrBook1 = AddrBook.new.addMe(peer1).add(peer2);
		addrBook2 = AddrBook.new.addMe(peer2).add(peer1);
	}

	tearDown {
		objectSpace1.free;
		objectSpace2.free;
	}

	test_dataSpace {
		var testObject1, testObject2;
		objectSpace1 = OSCObjectSpace(addrBook1);
		testObject1 = Complex(1.0, 0.0); // we can test this easily for equality
		objectSpace1.put(\cplx, testObject1);

		1.0.wait;
		"Testing objectSpaceSync:".postln;
		objectSpace2 = OSCObjectSpace(addrBook2);
		objectSpace2.sync;

		this.asynchAssert({objectSpace2[\cplx].notNil}, {objectSpace1[\cplx] == objectSpace2[\cplx] == testObject1}, "objectspace sync failed", 2);

		"Testing objectSpacePut:".postln;
		testObject2 = Polar(0.5, pi);
		objectSpace2[\polar] = testObject2;

		this.asynchAssert({objectSpace1[\polar].notNil}, {objectSpace1[\polar] == objectSpace2[\polar] == testObject2}, "dataspace put failed", 2);

	}
}

	