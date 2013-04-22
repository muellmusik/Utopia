NMLAbstractAuthenticator {

	authenticate {|peer, successFunc, failureFunc| this.subclassResponsibility }

}

// convenience for when no authentication is required
NonAuthenticator : NMLAbstractAuthenticator {

	*authenticate {|peer, successFunc, failureFunc| successFunc.value }

}

// uses a collection known to all participants as a challenge test
// use something large, like the text of War and Peace ;-)
// this could possibly have-a separate responder app, so that that could be created separately in centralised systems (e.g. Register/strant)
ChallengeAuthenticator : NMLAbstractAuthenticator {
	classvar <>timeOut = 5;
	var challenge, oscPath, challInds, challResponseOSCFunc;

	*new {|challenge, oscPath = '/challengeAuth'| ^super.newCopyArgs(challenge, oscPath).init; }

	init {
		// OSC sends Strings as Symbols, which is a minor pain, so convert Strings to Arrays of ASCII here
		if(challenge.isKindOf(String), { challenge = challenge.ascii });
		challInds = Pn(Plazy({Pshuf((0..(challenge.size - 1)), 1)}), inf).asStream;
		this.makeOSCFunc;
	}

	makeOSCFunc {
		challResponseOSCFunc = OSCFunc({|msg, time, addr|
			var inds;
			inds = msg[1..];
			addr.sendMsg(*([oscPath ++ "-challenge-reply"] ++ challenge[inds]));
		}, oscPath ++ "-challenge").fix;
	}

	free { challResponseOSCFunc.free; }

	authenticate {|peer, successFunc, failureFunc|
		var inds, vals, challReplyOSCFunc, testResult;

		#inds, vals = this.generateChallenge;

		challReplyOSCFunc = OSCFunc({|msg, time, addr|
			var challVals;
			challVals = msg[1..];
			testResult = challVals == vals;
			if(testResult, {
				successFunc.value;
			}, {
				"ChallengeAuthenticator challenge failed! Peer: %\n".format(peer).warn;
				testResult = false;
				failureFunc.value;
			});
		}, oscPath ++ "-challenge-reply", peer.addr).oneShot;
		peer.addr.sendMsg(*([oscPath ++ "-challenge"] ++ inds));

		//timeOut
		SystemClock.sched(timeOut, {
			if(testResult.isNil, {
				challReplyOSCFunc.free;
				"ChallengeAuthenticator challenge timed out! Peer: %\n".format(peer).warn;
				failureFunc.value;
			});
		});
	}

	generateChallenge {
		var inds, vals;
		inds = challInds.nextN(3);
		vals = challenge[inds];
		^[inds, vals];
	}
}

// Group Password authenticator
// All peers use the same password
// You need the password to decrypt challenge responses, so this should be bidirectionally secure
// i.e. you can't read someones password response, unless you know the password
GroupPasswordAuthenticator : NMLAbstractAuthenticator {
	classvar <>timeOut = 5;
	var password, oscPath, encryptor, oscFunc;

	*new {|password, oscPath = '/passwordAuth', encryptor| ^super.newCopyArgs(password, oscPath, encryptor).init; }

	init {
		// passwords should not be sent plaintext, so by default we use this with the same password
		encryptor = encryptor ?? { OpenSSLSymEncryptor(password)};
		this.makeOSCFunc;
	}

	makeOSCFunc {
		oscFunc = OSCFunc({|msg, time, addr|
			var encrypted;
			encrypted = encryptor.encryptText(password);
			addr.sendMsg(oscPath ++ "-challenge-reply", encrypted);
		}, oscPath ++ "-challenge").fix;
	}

	free { oscFunc.free; }

	authenticate {|peer, successFunc, failureFunc|
		var challReplyOSCFunc, testResult;

		challReplyOSCFunc = OSCFunc({|msg, time, addr|
			var encrypted, decrypted;
			encrypted = msg[1].asString;
			decrypted = encryptor.decryptText(encrypted);
			testResult = decrypted == password;

			if(testResult, {
				successFunc.value;
			}, {
				"GroupPasswordAuthenticator challenge failed! Peer: %\n".format(peer).warn;
				testResult = false;
				failureFunc.value;
			});
		}, oscPath ++ "-challenge-reply", peer.addr).oneShot;
		peer.addr.sendMsg(oscPath ++ "-challenge");

		//timeOut
		SystemClock.sched(timeOut, {
			if(testResult.isNil, {
				challReplyOSCFunc.free;
				"GroupPasswordAuthenticator challenge timed out! Peer: %\n".format(peer).warn;
				failureFunc.value;
			});
		});
	}
}