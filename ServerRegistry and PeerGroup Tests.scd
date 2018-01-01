/* ServerRegistry syncs info about Servers between the Peers in the supplied AddrBook. This can be the simple case of one server per Peer, but arbitrary configs are also possible. */
~reg = ServerRegistry(~addrBook);

// Convenience method to add the default server on this machine under my name
~reg.addMyServer;

/* Create a PeerGroup manager to create and sync groups between users. PeerGroups can be clients, servers, or anything which matches a name to an address.
In this case we want to have groups of servers, so we'll pass the ServerRegistry, but it also works with clients and AddrBook */
~gm = PeerGroupManager(~reg, '/serverGroups');

// make a server group. Form is \groupName, [key1, key2...]
~gm.add(\twoServers, ~reg.keys.asArray.scramble.keep(2)); // make a group

~reg.keys; // all the keys in the ServerRegistry

x = Synth(\default, target:~reg.values.choose.postln) // pick a random server
x.free;
// we can use any method that Array understands, since PeerGroup is an Array subclass!
Synth(\default, target:~gm[\twoServers].choose)

// similarly, we can use any list pattern with a PeerGroup, since it is an Array
Pbind(\server, Pseq(~gm[\twoServers], inf), \dur, 0.1).play

Pbind(\server, Pseq(~reg.values, inf), \dur, 0.1, \degree, Pseq((0..9), 1)).play


// AddrBook methods which produce PeerGroups
~addrBook.peers; // a PeerGroup with everybody
~addrBook.others; // a PeerGroup with everybody else
~addrBook.onlinePeers; // a PeerGroup with everybody currently online


