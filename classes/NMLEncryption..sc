NMLAbstractEncryptor {
	var password;

	encryptText { |text| ^this.subclassResponsibility }

	decryptText { |text| ^this.subclassResponsibility }
}

NonEncryptor : NMLAbstractEncryptor {

	*encryptText { |text| ^text }

	*decryptText { |text| ^text }
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
		^"% enc -d -% -a -pass pass:% <<< %".format(cmdPath, cipher ? defaultCipher, password, text).unixCmdGetStdOut;
	}
}
