# Distributed_File_Service
Distributed File Service with Consistency and 2PC Deletions

Objectives:
1. An introduction to sockets.
2. Exposure to multithreading.
3. Exposure to real-time directory monitoring.
4. An introduction to file consistency.
5. Exposure to Push/Pull/Invalidation Notices.

A Dropbox like distributed file service application written in Java with a Java Swing based GUI.The application consists of a server process and three clients. Each client
process will connect to the server over a socket. The server will handle all three clients concurrently. 

Clients will designate a directory on their system to serve as their shared directory. Any file placed into that directory will automatically be uploaded to the server. Once the server receives the new file, it will send that file to the remaining clients. Clients will place the received file into their shared directory.

## Consistency:
If a modification is made to a file shared by all three clients by any one of the clients then the copy of that file disagrees with all the other copies and the system is said to be inconsistent

The client will automatically detect that the file has been updated and Push those updates to the file server. When the server receives a file update from a client, it will issue Invalidation Notices to the remaining clients.


## 2 Phase Commit Deletions:
Any client may delete any file from its shared directory. This deletion will happen in the native file
manager of the OS. The program will monitor the directory in real-time to determine if a file has been removed. Upon recognition of a file’s removal, a client must obtain consent to delete that file from other clients via two-phase commit.

The client that initiated the deletion will send an instruction to delete the file, along with a vote-request, to the other clients, then assume the role of coordinator. The remaining clients will then assume the role of participants. If all clients vote to commit, the file may be deleted across all clients. If a distributed commit is not obtained, the file must be retained across all clients and reinstated on the client that initiated the deletion.

Clients will vote to commit or abort based on a random probability. After receiving a vote request, the clients will wait three seconds prior to voting. The user will be notified of a client’s vote on that respective client’s GUI. Once a client has initiated a deletion, the reminder of the process will be handled without user intervention.
