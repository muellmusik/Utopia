// for history, etc.
CodeRelay {
	var addrBook, oscPath, oscFunc, <>post;

	*new {|addrBook, oscPath = '/codeRelay', post = false|
		^super.newCopyArgs(addrBook, oscPath, post).init;
	}

	init {
		var interpreter;
		this.makeOSCFunc;
		interpreter = thisProcess.interpreter;
		interpreter.codeDump = interpreter.codeDump.addFunc({ |code|
			addrBook.sendAll(addrBook.me.name, code);
		});
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
		}, oscPath);
	}
}

// this just adds Servers
// need to send code as well
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
}