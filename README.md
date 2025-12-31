# Java Distributed Key–Value Store

A **from-scratch distributed key–value store** built in Java to understand how real backend systems behave under **failure, concurrency, and partial availability** — without hiding complexity behind frameworks.

This project is not a CRUD demo or a Redis clone.
It is an **educational distributed system** designed to make routing, replication, and consistency tradeoffs explicit and explainable.

Repository:
[https://github.com/theroycode/java-distributed-kv-store](https://github.com/theroycode/java-distributed-kv-store)

---

## Why this project exists

Most backend projects work as long as:

* there is one server,
* nothing crashes,
* and consistency is assumed.

Real systems break those assumptions.

This project was built to answer questions like:

* What actually happens when a node goes down?
* How does a system decide *where* data should live?
* Why do replication bugs turn into infinite loops?
* How does “eventual consistency” show up in practice?

The goal is **understanding**, not feature count.

---

## System overview

The system runs as **multiple independent JVM processes** (nodes).
Each node:

* has its own memory,
* exposes HTTP endpoints,
* is aware of the cluster,
* can accept client requests.

Data is **partitioned and replicated**, not shared.

High-level architecture:

* Any node can accept a client request
* Key ownership is decided deterministically using hashing
* Non-owner nodes forward requests to the correct node
* Each key has:

    * one **primary owner**
    * one **replica** for fault tolerance
* Writes replicate asynchronously
* Reads fall back to replicas if the primary is unavailable

---

## Features implemented

### Core distributed behavior

* Deterministic key partitioning using hashing
* Stateless request routing (any node can serve clients)
* Primary–replica replication
* Asynchronous replication (eventual consistency)
* Read failover when the primary node is down
* Explicit separation of client traffic vs internal replication traffic

### Correctness under failure

* No shared memory between nodes
* Replica writes bypass routing and replication (loop prevention)
* Replica reads terminate locally when the primary is unavailable
* System remains partially available under node failure

---

## What makes this project non-trivial

This project intentionally surfaced and fixed **real distributed-systems bugs**, including:

* **Infinite replication loops**
  Replica writes were initially treated as client writes.
  Fix: enforce strict invariants, replica traffic must bypass routing and must never trigger replication.

* **Hanging reads during primary failure**
  Replica nodes were forwarding GET requests instead of terminating them locally.
  Fix: replica nodes serve local reads when the primary is unavailable.

These bugs were not syntax errors, they were **logical failures**, which is where distributed systems get difficult.

---

## Consistency model

This system implements **eventual consistency**.

* Writes go to the primary and replicate asynchronously
* Reads normally go to the primary
* If the primary is down, reads fall back to replicas
* Replica reads may return stale data

This tradeoff is **intentional** and explicit.

---

## What this project is NOT

This project does **not** attempt to be:

* A production database
* A Redis replacement
* Strongly consistent
* Backed by Raft / Paxos
* Persisted to disk

Missing features are a conscious design decision, not an oversight.

---

## Running the system locally

### Requirements

* Java 17+
* No external dependencies
* No frameworks

### Example cluster setup

Run three nodes as separate JVM processes.

Each node is started with:

* its own port
* the full cluster port list

Example:

```bash
# Node 1
java Main 8080 8080,8081,8082

# Node 2
java Main 8081 8080,8081,8082

# Node 3
java Main 8082 8080,8081,8082
```

---

## API usage

### PUT (store a value)

```bash
curl -X PUT "http://localhost:8080/put?key=user42&value=alice"
```

The request can be sent to **any node**.

### GET (retrieve a value)

```bash
curl "http://localhost:8081/get?key=user42"
```

If the primary is down, the system automatically serves the value from a replica (if available).

---

## Failure demonstration

1. Start all nodes
2. PUT a key
3. Stop the primary node
4. GET the same key from another node

The request succeeds via **replica read fallback**.

This behavior is logged explicitly.

---

## Code structure (high-level)

* `KVStore`
  Thread-safe in-memory storage

* `ClusterManager`
  Determines primary and replica ownership via hashing

* `PutHandler`
  Handles routing, primary writes, replication, and loop prevention

* `GetHandler`
  Handles routing, primary reads, and replica read fallback

* `RequestForwarder`
  Internal HTTP client for node-to-node communication

---

## Design principles followed

* Explicit control flow over magic
* Correctness before optimization
* Fail visibly, not silently
* Invariants enforced in code, not comments
* Every distributed decision must be explainable

---

## Who this project is for

* Backend engineers who want to understand distributed systems beyond theory
* Students preparing for backend / infrastructure interviews
* Engineers who want to see *why* systems fail, not just *that* they fail

---

## Future extensions (intentionally out of scope)

* Leader election
* Replica promotion
* Strong consistency guarantees
* Disk persistence
* Rebalancing on node join/leave
* Metrics and observability

These are natural next steps, but are excluded to keep the system understandable and focused.

---

## Final note

This project is complete in the way that matters:
it exposes real distributed systems problems and solves them deliberately.

If you are reading this as a recruiter or engineer, the interesting part is not the API, it’s the **reasoning behind every design choice**.

---


