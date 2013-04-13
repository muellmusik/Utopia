// for history, etc.
CodeRelay {
	var addrBook, <>post, oscPath, codeDumpFunc, oscFunc;

	*new {|addrBook, post = false, oscPath = '/codeRelay', codeDumpFunc|
		^super.newCopyArgs(addrBook, post, oscPath, codeDumpFunc).init;
	}

	init {
		var interpreter;
		this.makeOSCFunc;
		codeDumpFunc = codeDumpFunc ? { |code|
			addrBook.sendAll(oscPath, addrBook.me.name, code);
		};
		interpreter = thisProcess.interpreter;
		interpreter.codeDump = interpreter.codeDump.addFunc(codeDumpFunc);
	}

	makeOSCFunc {
		oscFunc = OSCFunc({|msg, time, addr|
			var name, code;
			name = msg[1];
			code = msg[2];
			this.changed(\code, name, code);
			if(post, {
				(name.asString ++ ":\n" ++ code).postln;
				Char.nl.post;
			});
		}, oscPath, recvPort: addrBook.me.addr.port);
	}

	free { oscFunc.free; }
}

// this just adds Servers
// need to send code as well
// actually won't work as is, since we need a server, not a NetAddr
// should we make a server here?
// We can store a server in AddrBook, but that makes it slightly awkward for server subclasses
SynthDefRelay {
	var addrBook, libName;

	*new {|addrBook, libName = \global|
		^super.newCopyArgs(addrBook, libName).init;
	}

	init {
		var lib;
		lib = SynthDescLib.getLib(libName);
		addrBook.do({|citizen| lib.addServer(citizen.serverAddr) });
	}

	free {
		var lib;
		lib = SynthDescLib.getLib(libName);
		addrBook.do({|citizen| lib.removeServer(citizen.serverAddr) });
	}
}

// shared network dataspaces

AbstractOSCDataSpace {
	var addrBook, oscPath, oscFunc, syncRecOSCFunc, syncRequestOSCFunc, dict;

	init {|argAddrBook, argOSCPath|
		addrBook = argAddrBook;
		oscPath = argOSCPath;
		dict = IdentityDictionary.new;
		this.makeSyncRequestOSCFunc;
		this.makeOSCFunc;
	}

	put {|key, value| dict[key] = value; this.changed(\val, key, value); this.updatePeers(key, value);}

	at {|key| ^dict[key] }

	makeOSCFunc { this.subclassResponsibility }

	makeSyncRequestOSCFunc {
		syncRequestOSCFunc = OSCFunc({|msg, time, addr|
			var pairs;
			pairs = this.getPairs;
			addr.sendMsg(*([oscPath ++ "-sync-reply"] ++ pairs));
		}, oscPath ++ "-sync", recvPort: addrBook.me.addr.port);
	}

	getPairs { this.subclassResponsibility }

	updatePeers {|key, value| this.subclassResponsibility }

	free { oscFunc.free; syncRecOSCFunc.free; }

	sync {|addr|
		var syncAddr;
		syncAddr = addr ?? { addrBook.citizens.detect({|cit| cit.online }).addr }; // look for the first online one
		syncAddr.notNil.if({
			syncRecOSCFunc = OSCFunc({|msg, time, addr|
				var pairs;
				pairs = msg[1..];
				pairs.pairsDo({|key, val|
					if(dict[key] != val, {
						dict[key] = val;
						this.changed(\val, key, val);
					});
				});
			}, oscPath ++ "-sync-reply", syncAddr).oneShot;
			syncAddr.sendMsg(oscPath ++ "-sync");
		});
	}
}

OSCDataSpace : AbstractOSCDataSpace {

	*new {|addrBook, oscPath = '/oscDataSpace'|
		^super.new.init(addrBook, oscPath);
	}

	makeOSCFunc {
		oscFunc = OSCFunc({|msg, time, addr|
			var key, val;
			key = msg[1];
			val = msg[2];
			dict[key] = val;
			this.changed(\val, key, val);
		}, oscPath, recvPort: addrBook.me.addr.port);
	}

	getPairs { ^dict.getPairs }

	updatePeers {|key, value| addrBook.sendExcluding(\addrBook.me.name, oscPath, key, value); }
}

// **** should this just be a more straightforward thing that issues challenges at start?

// the following represents a security risk, since people could use a pseudo-object to inject undesirable code
// a challenge test is thus used before allowing a peer to alter a value
// Use some large collection as a challenge, e.g. the text of War and Peace
OSCObjectSpace : AbstractOSCDataSpace {
	classvar challengeWait = 0.001, challengeTimeOut;
	var <>challenge, challDict, challIndsList, challsIssued, challOSCFunc;

	*new {|addrBook, challenge, oscPath = '/oscDataSpace'|
		^super.new.challenge_(challenge).init(addrBook, oscPath);
	}

	makeOSCFunc {
		challDict = IdentityDictionary.new;
		challsIssued = Dictionary.new;
		challIndsList = (0..(challenge.size - 1)).scramble;
		// respond to challenges
		challOSCFunc = OSCFunc({|msg, time, addr|
			var challInds;
			if(addrBook.addrs.includes(addr), {
				challInds = msg[1];
				addr.sendMsg(oscPath ++ "-challenge-reply", challenge[challInds]);
			}, {"OSCObjectSpace challenge from unrecognised addr: %\n".format(addr).warn;});
		}, oscPath ++ "-challenge", recvPort: addrBook.me.addr.port);

		oscFunc = OSCFunc({|msg, time, addr|
			var key, val;
			if(addrBook.addrs.includes(addr), {
				key = msg[1];
				val = msg[2].interpret;

				if(challDict[addr].isNil, {challDict[addr] = false;});
				if(challDict[addr], {
					dict[key] = val;
					this.changed(\val, key, val);
				}, {
					this.challengeAndWait(key, val, addr); // send a challenge and defer change
				});
			}, {"OSCObjectSpace access attempt from unrecognised addr: %\n".format(addr).warn;});
		}, oscPath, recvPort: addrBook.me.addr.port);
	}

	getPairs { ^dict.asSortedArray.collect({|pair| [pair[0], pair[1].asTextArchive]}).flat }

	challengeAndWait {|key, val, addr|
		var inds, vals, challReplyOSCFunc, cond, waitRoutine;
		// if we've already challenged, don't do it again
		if(challsIssued[addr].isNil, {
			challsIssued[addr] = Condition(false);
			#inds, vals = this.generateChallenge;

			challReplyOSCFunc = OSCFunc({|msg, time, addr|
				var challVals, testResult;
				challVals = msg[1];
				testResult = challVals == vals;
				if(testResult, {
					challDict[addr] = true;
					challsIssued[addr].test_(true).signal;
				}, { "OSCObjectSpace challenge failed! addr: %\n".format(addr).warn; });
			}, oscPath ++ "-challenge-reply", addr, addrBook.me.addr.port).oneShot;

			addr.sendMsg(oscPath ++ "-challenge", inds);
		});
		cond = challsIssued[addr];
		waitRoutine = {
			cond.wait;
			dict[key] = val;
			this.changed(\val, key, val);
		}.fork;
		SystemClock.sched(challengeTimeOut, {
			if(challDict[addr].not, { challReplyOSCFunc.free; waitRoutine.stop;   "OSCObjectSpace challenge timed out! addr: %\n".format(addr).warn;});
		});
	}

	updatePeers {|key, value|
		var challInds, challVals;
		addrBook.sendExcluding(\addrBook.me.name, oscPath, key, value.asTextArchive);
	}

	generateChallenge {
		var inds, vals;
		inds = { challenge.size.rand } ! 3;
		vals = challenge[inds];
		^[inds, vals];
	}

	put {|key, value| value.checkCanArchive; super.put(key, value); } // at least this warns

	sync {|addr|
		addr = addr ?? { challDict.keys.detect({|testAddr| challDict[testAddr] }) };
		addr.notNil.if({ super.sync(addr) });
	}
}