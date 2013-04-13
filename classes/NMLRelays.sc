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
		syncAddr = addr ?? { addrBook.citizens.reject({|cit| cit == addrBook.me }).detect({|cit| cit.online }).addr }; // look for the first online one who's not me
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
			if(addrBook.addrs.includesEqual(addr), {
				key = msg[1];
				val = msg[2];
				dict[key] = val;
				this.changed(\val, key, val);
			}, {"OSCDataSpace access attempt from unrecognised addr: %\n".format(addr).warn;});
		}, oscPath, recvPort: addrBook.me.addr.port);
	}

	getPairs { ^dict.getPairs }

	updatePeers {|key, value| addrBook.sendExcluding(addrBook.me.name, oscPath, key, value); }
}

// the following represents a security risk, since people could use a pseudo-object to inject undesirable code
// It thus should only be used on a secure network with trusted peers, or with an authenticated addrBook (e.g. using ChallengeAuthenticator)
// It also has the option to reject instances of Event and subclasses (rejects by default)
OSCObjectSpace : AbstractOSCDataSpace {
	var <>acceptEvents;

	*new {|addrBook, acceptEvents = false, oscPath = '/oscObjectSpace'|
		^super.new.acceptEvents_(acceptEvents).init(addrBook, oscPath);
	}

	makeOSCFunc {
		oscFunc = OSCFunc({|msg, time, addr|
			var key, val;
			if(addrBook.addrs.includesEqual(addr), {
				key = msg[1];
				val = msg[2].asString.interpret; // OSC sends Strings as Symbols
				if(acceptEvents || val.isKindOf(Event).not, {
					dict[key] = val;
					this.changed(\val, key, val);
				}, { "OSCObjectSpace rejected event % from addr: %\n".format(val, addr).warn; });
			}, {"OSCObjectSpace access attempt from unrecognised addr: %\n".format(addr).warn;});
		}, oscPath, recvPort: addrBook.me.addr.port);
	}

	getPairs { ^dict.asSortedArray.collect({|pair| [pair[0], pair[1].asTextArchive]}).flatten }

	updatePeers {|key, value|
		addrBook.sendExcluding(addrBook.me.name, oscPath, key, value.asTextArchive);
	}

	put {|key, value| value.checkCanArchive; super.put(key, value); } // at least this warns

}