~reg = ServerRegistry(~addrBook);
~reg.addMyServer;
~gm = PeerGroupManager(~reg, '/serverGroups');

~gm.add(\us, [\scottw, \soctt]); // my laptop and test machine; use your own names

~reg.keys

Synth(\default, target:~reg[\soctt]) // pick a server

// we can use any method that Array understands, since PeerGroup is an Array subclass
Synth(\default, target:~gm[\us].choose)

// we can use any list pattern with a PeerGroup, since it is an Array
Pbind(\server, Pseq(~gm[\us], inf)).play

~addrBook.peers; // a PeerGroup with everybody
~addrBook.others; // a PeerGroup with everybody else
~addrBook.onlinePeers; // a PeerGroup with everybody currently online


