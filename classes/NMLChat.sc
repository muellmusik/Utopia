// simple chat class
// dependants can do something with it
// use different oscPath for different 'channels' eg Shout
Chatter {
	var addrBook, <>post, oscPath, encryptor, oscFunc;

	*new {|addrBook, post = true, oscPath = '/chat', encryptor|
		^super.newCopyArgs(addrBook, post, oscPath, encryptor ?? { NonEncryptor }).makeOSCFunc;
	}

	makeOSCFunc {
		oscFunc = OSCFunc({|msg, time, addr|
			var name, chat;
			name = msg[1];
			chat = encryptor.decryptText(msg[2]);
			this.changed(\chat, name, chat);
			if(post, {
				(name.asString ++ ": " ++ chat).postln;
			});
		}, oscPath, recvPort: addrBook.me.addr.port).fix;
	}

	free { oscFunc.free; }

	send {|chat|
		addrBook.sendAll(oscPath, addrBook.me.name, encryptor.encryptText(chat));
	}

	sendPrivate {|name, chat|
		addrBook.send(name, oscPath, addrBook.me.name, encryptor.encryptText(chat));
	}
}