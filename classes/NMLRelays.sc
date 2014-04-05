// for shared code history, etc.
CodeRelay {
	var addrBook, <>post, oscPath, encryptor, codeDumpFunc, oscFunc;
	var <>private = false, <>onlyWorkingCode = true;
	var historyFunc;

	*new {|addrBook, post = false, oscPath = '/codeRelay', encryptor, codeDumpFunc|
		^super.newCopyArgs(addrBook, post, oscPath, encryptor, codeDumpFunc).init;
	}

	init {
		var interpreter;
		encryptor = encryptor ?? { NonEncryptor }; // NonEncryptor uses noops

		codeDumpFunc = codeDumpFunc ?? { { |code, result, func|
			if (private or: { onlyWorkingCode and: func.isNil }) {
				"dont send";
			} {
				addrBook.sendAll(oscPath, addrBook.me.name, encryptor.encryptText(code));
			};
		} };
		interpreter = thisProcess.interpreter;
		interpreter.codeDump = interpreter.codeDump.addFunc(codeDumpFunc);
		this.makeOSCFunc;
	}

	makeOSCFunc {
		oscFunc = OSCFunc({|msg, time, addr|
			var name, code;
			if(addrBook.addrs.includesEqual(addr), {
				name = msg[1];
				code = encryptor.decryptText(msg[2]);
				this.changed(\code, name, code);
				if(post, {
					(name.asString ++ ":\n" ++ code).postln;
					Char.nl.post;
				});
			}, {"CodeRelay access attempt from unrecognised addr: %\n".format(addr).warn;});
		}, oscPath, recvPort: addrBook.me.addr.port).fix;
	}

	free {
		var interpreter;
		this.releaseDependants;
		oscFunc.free;
		interpreter = thisProcess.interpreter;
		interpreter.codeDump = interpreter.codeDump.removeFunc(codeDumpFunc);
	}

	addHistory {
		historyFunc = historyFunc ?? { { |changer, what, who, text|
			History.enter(text.asString, who);
		}};
		this.addDependant(historyFunc);
	}

	removeHistory {
		this.removeDependant(historyFunc);
	}
}

// This now uses binaryArchive for safety reasons, as this avoids the use of the interpreter
// However, this could cause problems if you send to someone with a different version of SC
// Possibly using the textArchive should be an option
SynthDescRelay {
	var addrBook, oscPath, libName, encryptor, lib, oscFunc;
	var justAddedRemote = false;

	*new {|addrBook, oscPath = '/synthDefRelay', libName = \global, encryptor|
		^super.newCopyArgs(addrBook, oscPath, libName).init;
	}

	init {
		lib = SynthDescLib.getLib(libName);
		lib.addDependant(this);
		encryptor = encryptor ?? { NonEncryptor }; // NonEncryptor uses noops
		this.makeOSCFunc;
	}

	makeOSCFunc {
		oscFunc = OSCFunc({|msg, time, addr|
			var desc, defcode, stream;
			if(addrBook.addrs.includesEqual(addr), {
				stream = CollStream(encryptor.decryptBytes(msg[2]));
				stream.getInt32; // 'SCgf'
				stream.getInt32; // version
				stream.getInt16; // 1
				desc = SynthDesc.new.readSynthDef2(stream, true);
				defcode = encryptor.decryptText(msg[1]);
				if(desc.isKindOf(SynthDesc), { // check for safety
					justAddedRemote = true;
					lib.add(desc);
					this.changed(\synthDesc, desc, defcode.asString);
				}, { "SynthDescRelay received non-SynthDesc object: %. Object discarded".format(desc).warn; });
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
		var desc, def;
		switch(what,
			\synthDescAdded, {
				// If we've just received one from somebody else, don't let that trigger another send and a never ending loop
				if(justAddedRemote.not, {
					desc = moreArgs[0];
					if(desc.isKindOf(SynthDesc), { // check for safety
						def = desc.def;
						addrBook.sendExcluding(addrBook.me.name, oscPath, encryptor.encryptText(def.asCompileString), encryptor.encryptBytes(def.asBytes));
					}, { "SynthDescRelay updated with non-SynthDesc object: %".format(desc).warn; });
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
		this.sync;
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
		}, oscPath ++ "-sync", recvPort: addrBook.me.addr.port).fix;
	}

	getPairs { this.subclassResponsibility }

	updatePeers {|key, value| this.subclassResponsibility }

	free { oscFunc.free; syncRecOSCFunc.free; }

	sync {|addr| this.subclassResponsibility }
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
		}, oscPath, recvPort: addrBook.me.addr.port).fix;
	}

	getPairs { ^dict.getPairs }

	updatePeers {|key, value| addrBook.sendExcluding(addrBook.me.name, oscPath, key, value); }

	sync {|addr, period = 0.3, timeout = inf|
		var syncAddr, started, waiting = true, peer;
		{
			started = thisThread.seconds;
			while({waiting && (thisThread.seconds - started < timeout)}, {
				syncAddr = addr ?? {
					peer = addrBook.peers.reject({|peer| peer == addrBook.me }).detect({|peer| peer.online });
					peer.notNil.if({peer.addr}, nil);
				}; // look for the first online one who's not me
				syncAddr.notNil.if({
					syncRecOSCFunc.isNil.if({
						syncRecOSCFunc = OSCFunc({|msg, time, addr|
							var pairs;
							waiting = false;
							pairs = msg[1..];
							pairs.pairsDo({|key, val|
								if(dict[key] != val, {
									dict[key] = val;
									this.changed(\val, key, val);
								});
							});
						}, oscPath ++ "-sync-reply", syncAddr).oneShot;
					});
					syncAddr.sendMsg(oscPath ++ "-sync");
				});
				period.wait;
			});
		}.fork;
	}
}

// the following represents a security risk, since people could use a pseudo-object to inject undesirable code
// It is thus best used on a secure network with trusted peers, or with an authenticated addrBook (e.g. using ChallengeAuthenticator)
// and/or using password protected encryption
// It also has the option to reject instances of Event and subclasses (rejects by default)
// This currenly uses binaryArchive for safety reasons, as this avoids the use of the interpreter, which could execute arbitrary code
// However, this could cause problems if you send to someone with a different version of SC
// Possibly using the textArchive should be an option
OSCObjectSpace : AbstractOSCDataSpace {
	var <>acceptEvents, encryptor;

	*new {|addrBook, acceptEvents = false, oscPath = '/oscObjectSpace', encryptor|
		^super.new.acceptEvents_(acceptEvents).init(addrBook, oscPath, encryptor);
	}

	init {|argAddrBook, argOSCPath, argEncryptor|
		encryptor = argEncryptor ?? { NonEncryptor }; // NonEncryptor uses noops
		super.init(argAddrBook, argOSCPath);
	}

	makeOSCFunc {
		oscFunc = OSCFunc({|msg, time, addr|
			var key, val;
			if(addrBook.addrs.includesEqual(addr), {
				key = msg[1];
				val = encryptor.decryptBytes(msg[2]).unarchive;
				if(acceptEvents || val.isKindOf(Event).not, {
					dict[key] = val;
					this.changed(\val, key, val);
				}, { "OSCObjectSpace rejected event % from addr: %\n".format(val, addr).warn; });
			}, {"OSCObjectSpace access attempt from unrecognised addr: %\n".format(addr).warn;});
		}, oscPath, recvPort: addrBook.me.addr.port).fix;
	}

	getPairs { ^dict.asSortedArray.collect({|pair| [pair[0], encryptor.encryptBytes(pair[1].asBinaryArchive)]}).flatten }

	updatePeers {|key, value|
		addrBook.sendExcluding(addrBook.me.name, oscPath, key, encryptor.encryptBytes(value.asBinaryArchive));
	}

	sync {|addr, period = 0.3, timeout = inf|
		var syncAddr, started, waiting = true, peer;
		{
			started = thisThread.seconds;
			while({waiting && (thisThread.seconds - started < timeout)}, {
				syncAddr = addr ?? {
					peer = addrBook.peers.reject({|peer| peer == addrBook.me }).detect({|peer| peer.online });
					peer.notNil.if({peer.addr}, nil);
				}; // look for the first online one who's not me
				syncAddr.notNil.if({
					syncRecOSCFunc.isNil.if({
						syncRecOSCFunc = OSCFunc({|msg, time, addr|
							var pairs;
							waiting = false;
							pairs = msg[1..];
							pairs.pairsDo({|key, val|
								val = encryptor.decryptBytes(val).unarchive;
								if(dict[key] != val, {
									dict[key] = val;
									this.changed(\val, key, val);
								});
							});
						}, oscPath ++ "-sync-reply", syncAddr).oneShot;
					});
					syncAddr.sendMsg(oscPath ++ "-sync");
				});
				period.wait;
			});
		}.fork;
	}

	put {|key, value|
		value.checkCanArchive; // at least this warns
		if(acceptEvents || value.isKindOf(Event).not, {
			super.put(key, value);
		}, { "OSCObjectSpace rejected event %\n".format(value).warn; });
	}

}