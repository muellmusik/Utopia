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