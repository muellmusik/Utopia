NMLAbstractEncryptor {
	var password;

	encryptText { |text| ^this.subclassResponsibility }

	decryptText { |text| ^this.subclassResponsibility }

	encryptBytes { |int8array| ^this.subclassResponsibility }

	decryptBytes { |encryptedText| ^this.subclassResponsibility }
}

NonEncryptor : NMLAbstractEncryptor {

	*encryptText { |text| ^text }

	*decryptText { |text| ^text }

	*encryptBytes { |int8array| ^int8array }

	*decryptBytes { |encryptedText| ^encryptedText }
}

// requires OpenSSL in your path
OpenSSLSymEncryptor : NMLAbstractEncryptor {
	classvar <>defaultCipher = "aes-128-cbc", <>cmdPath = "openssl";
	var cipher;

	*new { |password, cipher| ^super.newCopyArgs(password, cipher) }

	encryptText { |text|
		^"% enc -% -a -salt -pass pass:% <<< \"%\"".format(cmdPath, cipher ? defaultCipher, password, text).unixCmdGetStdOut;
	}

	decryptText { |text|
		var decrypt;
		decrypt = "% enc -d -% -a -pass pass:% <<< %".format(cmdPath, cipher ? defaultCipher, password, text).unixCmdGetStdOut;
		// openssl adds a trailing newline for some reason
		^decrypt.drop(-1);
	}

	encryptBytes { |int8array|
		var path, pipe, file, encrypted;
		path = Platform.defaultTempDir ++ "tmptext";
		pipe = Pipe("% enc -% -a -salt -pass pass:% -out %".format(cmdPath, cipher ? defaultCipher, password, path), "w");
		pipe.write(int8array);
		pipe.close;
		file = File(path, "r");
		encrypted = file.readAllString;
		file.close;
		//"rm %".format(path).unixCmd;
		^encrypted;
	}

	decryptBytes { |encryptedText|
		var pipe, decrypt, byte;
		pipe = Pipe("% enc -d -% -a -pass pass:% <<< %".format(cmdPath, cipher ? defaultCipher, password, encryptedText), "r");
		decrypt = Int8Array.new;
		while { byte = pipe.getInt8; byte.notNil } { decrypt = decrypt.add(byte) };
		pipe.close;
		^decrypt;
	}
}
