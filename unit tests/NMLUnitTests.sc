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