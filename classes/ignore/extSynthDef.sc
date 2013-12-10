// add this from 3.6.6 if using 3.6.5 or earlier!

+ SynthDescLib {
	add { |synthdesc|
		synthDescs.put(synthdesc.name.asSymbol, synthdesc);
		this.changed(\synthDescAdded, synthdesc);
	}
}