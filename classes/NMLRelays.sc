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
			if(addrBook.addrs.includesEqual(addr), {
				name = msg[1];
				code = msg[2];
				this.changed(\code, name, code);
				if(post, {
					(name.asString ++ ":\n" ++ code).postln;
					Char.nl.post;
				});
			}, {"CodeRelay access attempt from unrecognised addr: %\n".format(addr).warn;});
		}, oscPath, recvPort: addrBook.me.addr.port);
	}

	free { oscFunc.free; }
}

// Do we really need this? We could do it all with an OSCObjectSpace
SynthDescRelay {
	var addrBook, oscPath, libName, lib, oscFunc;
	var justAddedRemote = false;

	*new {|addrBook, oscPath = '/synthDefRelay', libName = \global|
		^super.newCopyArgs(addrBook, oscPath, libName).init;
	}

	init {
		lib = SynthDescLib.getLib(libName);
		lib.addDependant(this);
		this.makeOSCFunc;
	}

	makeOSCFunc {
		oscFunc = OSCFunc({|msg, time, addr|
			var desc;
			if(addrBook.addrs.includesEqual(addr), {
				desc = msg[1].asString.interpret;
				justAddedRemote = true;
				lib.add(desc);
				this.changed(\synthDesc, desc);
			}, {"SynthDescRelay access attempt from unrecognised addr: %\n".format(addr).warn;});
		}, oscPath, recvPort: addrBook.me.addr.port).fix;
	}

	free {
		oscFunc.free;
		lib.removeDependant(this);
	}

	update {|changed, what ...moreArgs|
		this.updateFromLib(what, *moreArgs);
	}

	updateFromLib {|what ...moreArgs|
		switch(what,
			\synthDescAdded, {
				// If we've just received one from somebody else, don't let that trigger another send and a never ending loop
				if(justAddedRemote.not, {
					addrBook.sendExcluding(addrBook.me.name, oscPath, moreArgs[0].asTextArchive);
				}, { justAddedRemote = false });
			}
		)
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

	keys { ^dict.keys }

	values { ^dict.values }

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