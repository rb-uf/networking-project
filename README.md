# Networking Project

Group Members:
- Oscar Rodas - BitTorrent algorithm code, thread code, bugfixes
- Don Chen - Socket code, thread code, bugfixes
- Richard Bachmann - Config file parsing code, bugfixes, final linux testing

## Status

The project is unfortunately only in a semi-functional state.
We were making good progress, but the complexity of our code caught up to us, making the final changes and bug fixes unfeasible.

Here's what we did accomplish:
- Code compiles.
- PeerProcess can be started on a CISE linux machine.
- Multiple PeerProcesses running on different CISE linux machines can connect and communicate.
- File chunks can be transmitted from one machine to other machines.

Here are the issues:
- Logging at one point worked, but stopped working shortly before the deadline.
- The PeerProcesses do not stop when all peers have the entire file.
- Killing one PeerProcess causes other peers to crash.
- The timing and ordering of starting PeerProcesses can sometimes cause crashes.
- The PeerProcesses do not choke and unchoke at the regular time intervals.


