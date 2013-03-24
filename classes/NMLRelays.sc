// for history, etc.
CodeRelay {
	var addrBook, <>post, oscPath, oscFunc;

	*new {|addrBook, post = false, oscPath = '/codeRelay'|
		^super.newCopyArgs(addrBook, post, oscPath).init;
	}

	init {
		var interpreter;
		this.makeOSCFunc;
		interpreter = thisProcess.interpreter;
		interpreter.codeDump = interpreter.codeDump.addFunc({ |code|
			addrBook.sendAll(oscPath, addrBook.me.name, code);
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