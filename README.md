# Real-Time Voting System — Kafka + Flink

**[Leia em português / Read this in Portuguese](README.pt-BR.md)**

Streaming vote tallying: votes come in through a REST API, go to Kafka, and a Flink job
enforces **one vote per voter** and keeps a running count by **candidate, state, city and
party**.

```
POST /api/v1/votes ──► ingest-api ──► [votes.cast] ──► Flink ──┬─► [results.by-candidate]
                            │                            │     ├─► [results.by-state]
                            │                          dedup   ├─► [results.by-city]
                            └──► [votes.receipts]         │     ├─► [results.by-party]
                                                          │     └─► [votes.rejected]
                                                          │
                                    [votes.accepted] ◄────┤
                                    [votes.windows]  ◄────┘
                                           │
                                           ▼
                                    merkle-service (Go) ──► [merkle.roots]
                                           │
                                           └──► GET /proof/{receipt} ◄── voting-web
                                                                        (verifies in
                                                                         the browser)
```

## Contents

[How to run](#how-to-run) · [Architecture](#architecture) · [Topics](#topics) ·
[Load test](#load-test) · [Merkle Tree](#merkle-tree-and-inclusion-proof) ·
[The voter's screen](#the-voters-screen) · [Persistence](#persistence-the-chain-journal) ·
[Guarantees](#guarantees) · [Receipt privacy](#privacy-note-on-the-receipt) ·
[Closing](#closing-the-vote) ·
[Watermark](#the-watermark-stall-and-why-heartbeats) ·
[Next phases](#next-phases)

## How to run

Requirements: Docker, a JDK 17+, Go 1.24+ and Node 20+ (only for `make test-web`; the page
itself has no build). The `Makefile` uses `/opt/homebrew/opt/openjdk@21` by default — override
it with `make JAVA_HOME=...`.

```bash
make test        # 157 tests (89 Java + 53 Go + 15 for the browser verifier)
make up          # Kafka (KRaft), Flink, ingest API, Merkle service, the screen and kafka-ui
make submit      # submits the tallying job
make bench       # load test with Gatling (10,000 votes, 10% duplicates)
make results     # running scoreboard by candidate and by state
make rejected    # rejected votes
make roots       # chain of Merkle roots already sealed
make proof RECEIPT=<hash>   # inclusion proof for a receipt
make chain       # chain state: journal identity, head and open windows
make down        # tears everything down and deletes the data
```

| Service | Address |
|---|---|
| Vote confirmation (the screen) | http://localhost:8084 |
| Ingest API | http://localhost:8081 |
| Flink UI | http://localhost:8082 |
| Merkle service (proofs) | http://localhost:8083 |
| Kafka UI | http://localhost:8080 |
| Kafka (from the host) | `localhost:29092` |

### Voting

```bash
curl -XPOST localhost:8081/api/v1/votes -H 'Content-Type: application/json' -d '{
  "voterId": "voter-1", "candidateId": "cand-1", "partyId": "PARTIDO-A",
  "state": "sp", "city": "Sao Paulo"
}'
# 201 {"receipt":"38641edb…","status":"ACCEPTED","castAt":"2026-09-06T07:40:22.299Z"}
```

`ACCEPTED` means **accepted for tallying**, not "counted". Uniqueness is decided further
downstream, by Flink, which holds the state of every voter. The receipt is what lets you check
the outcome later.

## Architecture

Maven modules — plus two that aren't Java — from the core to the edge. Dependencies only
point inward:

| Module | Role |
|---|---|
| `voting-domain` | **Plain Java.** Vote, uniqueness, receipt, tally. No Kafka, Flink, Spring or Jackson. |
| `voting-application` | Use cases and ports. Depends only on the domain. |
| `voting-contracts` | Events that travel through Kafka + translation to/from the domain. |
| `voting-ingest-api` | Adapters: inbound REST, outbound Kafka producer. |
| `voting-streaming` | Flink adapter: sources, sinks and thin functions that delegate to the domain. |
| `voting-benchmark` | Gatling load test over a fixed base of parties, candidates and municipalities. |
| `voting-merkle` | **Go.** Seals each window into a chained Merkle tree and serves inclusion proofs. |
| `voting-web` | **HTML and JavaScript, no build.** The screen where voters check their receipt — verification runs in their browser. |

The rule that holds the decoupling together: **`voting-domain/pom.xml` declares no external
dependencies** (only JUnit for tests). If something from the infrastructure needs to go in
there, it's a sign the modelling has leaked.

Two concrete consequences of this in the code:

- `TallyDimension` (domain) knows how to extract its own key from a vote. That's why the Flink
  job contains no "how to group by city" rule — it iterates over the dimensions. Adding a new
  dimension means adding a constant to the enum.
- `VoteAdmission` (domain) decides whether a vote gets in; Flink only **stores** each voter's
  previous receipt in keyed state. Swapping the state mechanism doesn't touch the rule, and the
  rule is testable without starting a cluster.

## Topics

| Topic | Key | Policy |
|---|---|---|
| `votes.cast` | `voterId` | 6 partitions |
| `votes.rejected` | `voterId` | duplicates and invalid votes, auditable |
| `votes.receipts` | receipt hash | compacted |
| `votes.accepted` | `voterId` | 6 partitions; the post-dedup stream, base of the tree |
| `votes.control` | — | 1 partition; the heartbeats that unstick the watermark |
| `votes.windows` | `windowId` | **1 partition**; the order of the markers defines the chain |
| `merkle.roots` | `windowId` | compacted; the published chain of roots |
| `results.by-candidate` / `-state` / `-city` / `-party` | dimension value | compacted |

The `votes.cast` key **is not decorative**: it's what puts all of a voter's votes in the same
partition, which is required for Flink's keyed dedup to see the duplicate. Changing it breaks
the "one vote per voter" rule without breaking a single unit test.

The result topics are compacted and keyed by dimension: Kafka keeps the latest count for each
key forever, so a consumer reading from the beginning rebuilds the full scoreboard.

## Load test

```bash
make bench                                              # 10,000 votes in 30s, 10% duplicates
make bench BENCH="-Dvotes=50000 -Dramp=60 -DduplicateRate=0.05"
# report in voting-benchmark/target/gatling/*/index.html
```

A reference run on this machine (Colima, 4 vCPU): 10,000 requests, **0 failures**, p95
**49 ms**, 322 req/s — and the tally closed at 8,986, exactly the number of distinct voters,
with 1,014 duplicates in `votes.rejected`.

### The fixed dataset

`voting-benchmark/src/main/resources/dataset/` is versioned rather than randomly drawn on each
run: two runs are only comparable if they contest the same election.

| File | Contents |
|---|---|
| `partidos.csv` | the 30 parties registered with the TSE (Brazil's electoral court), with ballot number and acronym |
| `candidatos.csv` | 12 candidates, one per party, with names generated by DataFaker (pt-BR) |
| `municipios.csv` | 87 municipalities covering all 27 states, with a sampling weight |

The `candidateId` is the **party's ballot number** (13, 22, 45…), as on the Brazilian voting
machine: the voter types the number of the presidential candidate's party. This keeps the keys
of `results.by-candidate` readable without looking up another table, and `partyId` is the
acronym — so `results.by-party` comes out as `PT`, `PL`, `NOVO`.

`make bench-data` regenerates `candidatos.csv` from `partidos.csv`. The seed is fixed
(`SEED = 2026`), so regenerating produces the same file: if the party list changes, the diff
shows who came in and who left, instead of a whole shuffled file.

### What is deterministic, and what isn't

`VoteFeeder` is a deterministic sequence: for the same seed, the n-th ballot is always the
same — same candidate, same municipality, same position of the duplicates. Which virtual user
picks up which ballot varies with thread scheduling, but no aggregate depends on that: the
totals by candidate, state, city and party are identical across runs.

Two deliberate choices in the distribution:

- **Voting intention is uneven** (28%, 24%, 12%…). A twelve-way tie wouldn't produce hot keys,
  which is exactly what stresses the tally's partitioning.
- **Municipalities are drawn by population weight.** São Paulo needs to get more votes than
  Rorainópolis, otherwise `results.by-state` comes out uniform and unrealistic.

The only thing that **changes** on each run is the voter namespace: `voterId` carries a
timestamped `runId`. Without it, a second run against the same cluster would have every vote
rejected — Flink's dedup remembers the voters from the previous run. Pin it with `-DrunId=...`
only when you want exactly that scenario.

## Merkle Tree and inclusion proof

The `voting-merkle` service (Go) seals each time window into an **RFC 6962** Merkle tree — the
same one used by Certificate Transparency — chained to the previous window. With the receipt
in hand, the voter gets a proof that their vote made it into the tally, and can check it
**without trusting the server**.

```bash
curl localhost:8083/proof/$RECEIPT   # inclusion proof + window root
make roots                           # the chain of roots already sealed
```

### Why it doesn't read `votes.receipts`

This is the central design decision. `votes.receipts` gets one record per vote that **reaches
the API**, including the ones Flink later rejects. A tree built on that topic would give an
inclusion proof for votes that were never counted — the exact opposite of the guarantee it
exists to provide.

The tree feeds on the **post-dedup** stream: Flink publishes to `votes.accepted` only the
admitted votes. Verified in practice: a duplicate vote shows up in `votes.receipts`, is
rejected as `DUPLICATE_VOTE`, and the service answers `404 RECIBO_NAO_SELADO` (receipt not
sealed) for its receipt.

`votes.accepted` also **doesn't carry the candidate**, for the same reason `votes.receipts`
doesn't: it's the basis of a public lookup, and would become a map of who voted for whom.

### Who closes the window

Flink, not the Go service. It already has a watermark based on vote time and exactly-once — it
can say "window [T, T+n) is closed, nothing else is coming". It publishes this to
`votes.windows` as `{windowId, count}`, and the Go service seals once it has gathered exactly
`count` leaves.

Sealing on **completeness**, rather than on a timeout, is what keeps a slow consumer from
producing a root that diverges from the tally. And if the count doesn't add up, there's a gap
— which stays visible, instead of becoming a silently incomplete tree.

`votes.windows` has **a single partition**, on purpose: the order of the markers is what
defines the sequence of the chain of roots.

### What makes the root reproducible

Within a window, leaves are sorted by receipt before becoming a tree. The root becomes a
function of the *set*, not of the arrival order — which varies on every run, since the leaves
come from 6 partitions in parallel. Without this sorting, no auditor could recompute the root.

Each checkpoint includes the hash of the previous one:

```
checkpoint_N = SHA-256(0x02 || checkpoint_{N-1} || root_N || windowId || size)
```

Rewriting an old window changes every link after it: the latest published root commits to the
entire history.

### How the voter checks

With receipt, proof and root, the math is RFC 6962's and fits in twenty lines in any language:

```
leaf = SHA-256(0x00 || bytes(receipt))
node = SHA-256(0x01 || left || right)
```

The `0x00`/`0x01` prefixes aren't decoration: without them, a leaf can be forged to pass as an
internal node (second-preimage attack). The chain's `0x02` exists for the same reason, so that
a link can't collide with a tree node.

A real run: 4 windows sealed (12+7+7+7 leaves), proof checked by an independent verifier
written in Python — forged leaf rejected, tampered path rejected, chain intact.

### The voter's screen

`voting-web` is the page where the receipt is checked — and its whole point is that
**verification runs in the voter's browser**, not on the server.

```bash
make up   # http://localhost:8084
```

The Go service delivers receipt, proof and root. Redoing the RFC 6962 math and deciding whether
it adds up is the job of `voting-web/public/verify.js`, on the machine of whoever asked. A
server that wanted to lie would have to forge SHA-256.

There's no build, framework or CDN: four files served by nginx, with no bundler or
minification. That isn't minimalism for taste — it's what makes it possible to claim that the
audited code is the code that runs. nginx also forwards `/api` to the Go service, so the page
doesn't depend on CORS being configured on the other side.

The tests (`make test-web`, with no `npm install` — there are no dependencies) do **not** check
the verifier against itself: the vectors come from `pkg/checkpoint`, the Go implementation. A
second implementation of the same algorithm is only worth something if it's checked against
the first. Besides valid proofs, they cover a forged leaf, a tampered path, a root from another
window, a path longer and shorter than the tree, an index outside it, and a window rewritten in
the middle of the chain.

What only exists in the browser has its own check, kept separate because it needs dependencies
(`cd voting-web && npm run test:browser`, with Playwright and Chrome): it starts a stub with the
`/proof` and `/roots` contract and drives Chrome through the page. The check that justifies
this file is the **dishonest server** one — the stub returns a tampered proof, and the screen
has to reject it on the voter's machine.

The screen also refuses to say what it doesn't know. A receipt without a proof has three
explanations — window still open, vote rejected as a duplicate, receipt doesn't exist — and it
states all three, instead of letting the voter assume the worst. And the positive verdict comes
with the caveat that completes the reasoning: proof and root came from the same server, so the
math proves inclusion in *that* root; confirming it's the published root means comparing the
checkpoint hash with the one in `merkle.roots` or with another observer's. The check-the-chain
button redoes every link, from genesis to the latest root, and points to the exact window where
it would break.

### Go module structure

The goal was to be as agnostic as possible, so the core doesn't know what a vote is:

| Package | Knows about |
|---|---|
| `pkg/merkle` | trees and proofs. **Zero dependencies**, zero election vocabulary. |
| `pkg/checkpoint` | batches of opaque leaves, sealed and chained. Depends only on `pkg/merkle`. |
| `pkg/wal` | a durable append-only log of opaque records. **Zero dependencies**. |
| `pkg/journal` | writes the chain's facts to `pkg/wal`. It's the implementation of `checkpoint.Store`. |
| `internal/voting` | the voting events and how they become leaves. |
| `internal/kafkaio` | the only package that knows the transport is Kafka. |
| `internal/api` | HTTP. |

`pkg/checkpoint` defines the `Store` interface and doesn't know what's on the other side — a
file, a database, an in-memory test. It still depends only on `pkg/merkle`.

There's also `cmd/vetores`, a development tool: it emits the test vectors the browser verifier
consumes (`make web-vetores`). It exists so that the JavaScript implementation is checked
against this one, and not against itself.

### Performance

```bash
cd voting-merkle && go test ./pkg/... -run '^$' -bench . -benchtime 200x
```

The benchmarks cover the paths that matter: sealing a window (`New`, `Seal`), serving a proof
(`Prove`, `Lookup`) and writing and restoring the chain (`Append`, `Sync`, `Restore`), in
batches of 1 thousand to 100 thousand leaves. The persistence numbers are in its own section,
further below.

Measured with profadvisor, in adjacent captures, on a batch of 100 thousand leaves:

| | before | after | allocations |
|---|---|---|---|
| `Prove` | 14.86 ms | **226 ns** | 99,988 → 1 |
| `New` | 24.99 ms | **20.58 ms** | 200,001 → 42 |

The `Tree` used to keep only the leaves and the root, so **every proof rebuilt the internal
nodes from scratch** — O(n) hashes to answer a single voter. By materializing the levels at
construction, the proof became a walk of O(log n) reads, with no hashing at all. The cost is
doubling the tree's memory (2n hashes instead of n): 6 MB for 100 thousand leaves.

The gain in `New` comes from reusing the hasher and allocating one buffer per level, instead of
one digest per node.

#### How to measure without fooling yourself

This machine's noise floor was measured with an A/A test — two captures of the **same code**,
where any difference is an artifact:

| benchmark scale | A/A deviation | reliable? |
|---|---|---|
| `New`, milliseconds | ±0.4 to 2.5% | yes |
| `Verify`, microseconds | ±0.2 to 1.1% | yes |
| `Prove`, nanoseconds, `-benchtime 200x` | ±10 to 20% | **no** |
| `Prove`, nanoseconds, `-benchtime 200000x` | ±0.05 to 0.31% | yes (up to 10 thousand leaves) |
| `Prove`, 100 thousand leaves, any benchtime | ±10% | **no** |

Two traps, in this order:

**`-benchtime` too low for nanosecond operations.** With `200x`, a 150 ns operation gives 30 µs
of work per sample — timer overhead dominates, and the p-value declares significant a
difference that is just jitter. The p-value measures whether the two samples differ, not
whether the change caused the difference.

**Captures separated in time.** With machine load varying between them, the same code has shown
up 33% slower. Always measure both variants back to back.

The 100-thousand-leaf case doesn't stabilize with more iterations: the levels add up to
~6.4 MB, larger than L2, and the proof touches one node per level at scattered addresses. At
that scale `Prove` is bound by cache misses, not computation — the variance comes from the
cache state between processes.

### Persistence: the chain journal

The chain lives on disk, in an append-only log (`/var/lib/merkle/chain.wal`) with three record
types: a leaf entered a batch, a batch was announced with a total, a batch was sealed. Nothing
is updated or deleted — rewriting the past is exactly what the hash chain exists to expose.

```bash
make chain            # journal storeId, chain head, open windows
make restart-merkle   # stops and starts only the service: the chain comes back from disk
```

#### Startup is an audit of its own disk

Restoring does **not** trust what's written: it rereads each batch's leaves, rebuilds the tree
and recomputes root and link, checking them against the recorded seal. If one byte of a leaf
changed on disk, the recomputed root diverges and the service **doesn't start**.

The alternative — store the root and serve it — would trade a loud failure at startup for wrong
proofs served confidently, possibly months later. Storing the leaves costs space; storing only
the root costs the guarantee itself.

`pkg/wal` makes the distinction that matters before that: a record that ends **before** what it
promises is the trace of a crash in the middle of an append — the file is truncated there, and
the lost operation comes back through Kafka's redelivery. A complete record with a **wrong CRC**
is something else: the bytes arrived and they're wrong. That's an error, not a ragged tail.
Treating both the same would silently discard everything from there on.

#### Resuming consumption without leaving gaps

With the chain on disk, the consumer can finally resume from a saved offset. Two rules make
this safe:

**The offset only advances after the `fsync`.** Whoever confirms consumption is whoever just saw
the journal reach the disk, and the commit is always manual — never in the background on an
interval, which could get ahead of the `fsync`. As long as this order holds, a committed offset
means "these leaves are written". Reversed, a crash would leave Kafka thinking those leaves had
already been handled, and the window would be sealed **without them** — the silent gap the
previous version avoided by rereading everything.

**The group name carries the journal's identity.** The file draws a random id on creation, and
the consumer group is `merkle-service-<id>`. As long as the volume survives, the group is the
same and consumption continues where it left off. If the volume is lost, the journal is born
with a new id, the group is new too, and reading restarts from the beginning of the topics —
which is correct, because there's no local state the old offset would correspond to. A
fixed-name group is exactly what would produce the tree with gaps.

The root is also only published to `merkle.roots` **after** the seal's `fsync`: you don't
publicly announce a commitment that can still disappear on a restart. There are few `fsync`s
(one per window), and they come before the announcement.

#### What restoring costs

```bash
cd voting-merkle && go test ./pkg/journal -run '^$' -bench . -benchtime 30x
```

| | cost | where it weighs |
|---|---|---|
| write one leaf | 327 ns, 1 allocation | per vote, no `fsync` |
| `fsync` of a 256-leaf batch | ~7 ms | ~27 µs per vote, amortized |
| restore 100 thousand leaves from disk | **~65 ms** | once, at startup |

The 65 ms include rebuilding each tree and rechecking each root. That's the comparison that
justifies the work: instead of rereading two Kafka topics from the beginning, startup reads a
local file sequentially.

#### What still holds

The volume isn't the source of truth, and losing it doesn't lose the tally: `votes.accepted` and
`votes.windows` still are, and the full rebuild still works — it's just slow. `merkle.roots`
stays compacted, so an external auditor rebuilds the published chain without talking to the
service.

The limit that was **not** solved is memory: the trees stay whole in RAM to serve proofs in
O(log n), so the process still grows with the number of votes. Serving proofs straight from
disk is another piece of work, and another performance trade-off.

## Guarantees

- **One vote per voter** — state per `voterId` in Flink. The second vote goes to
  `votes.rejected` with the reason and the receipt of the vote that actually counts.
- **Nothing is silently dropped** — duplicates, votes from another election and events that
  violate the invariants become auditable rejections. Only unreadable JSON is dropped (with a
  log and the `corruptRecords` counter), so that a corrupted message doesn't become a restart
  loop.
- **Exactly-once** — checkpointing every 10s and transactional sinks. The counts are
  cumulative: with `at-least-once`, a restart would reprocess votes already counted and the
  scoreboard would inflate. It costs latency — results show up at the end of each checkpoint.
  Turn it off with `--exactly.once false` if you want lower latency in development.
- **A vote is only confirmed after Kafka's ack** — publishing at ingestion is synchronous, with
  `acks=all` and idempotence. A `503` means "the vote is fine, the system just couldn't record
  it"; resending is safe, because the duplicate would be filtered at tallying.
- **Auditable inclusion proof** — each window is sealed in an RFC 6962 tree with a root chained
  to the previous one. The voter checks their receipt without trusting the server.
- **The deadline belongs to the server** — `castAt` is stamped on arrival and doesn't exist in
  the HTTP contract. The authority over closing is Flink, not the edge.

### Verified in practice

| Guarantee | Evidence |
|---|---|
| One vote per voter | 11,001 requests, 10,000 voters → tally closed at 8,986 (the distinct ones), 1,014 rejections |
| Exactly-once | `restart taskmanager` during the load: counts didn't inflate, dedup state preserved |
| Inclusion proof | independent verifier in Python: valid proof accepted, forged leaf and tampered path rejected |
| Duplicate without proof | the duplicate vote's receipt exists in `votes.receipts`, is rejected, and gets a `404` from the proof service |
| Closing | vote injected straight into Kafka with `castAt` after the deadline → `ELECTION_CLOSED` |
| Window without traffic | a lone vote sealed in ~45s thanks to the heartbeats (before: `404` indefinitely) |
| Verification in the browser | 14 checks in a real Chrome (`npm run test:browser`): proof tampered **by the server** rejected on the voter's machine, rewritten chain flagged at the right window |
| Chain restore | `pkg/journal` tests: roots, links and proofs rebuilt from disk; a tampered leaf in the file prevents startup |

The last two rows were verified outside the cluster — the first against a stub with the
`/proof` and `/roots` contract, the second at the package level. Restarting the Merkle service
against the running cluster (`make restart-merkle`) hasn't been exercised in a real run yet.

## Privacy note on the receipt

The receipt is `SHA-256(electionId | voterId | candidateId | castAt | pepper)`, where the
`pepper` is a server secret.

**The `pepper` is what prevents breaking ballot secrecy.** Without it, the candidate space is
small enough that anyone who knows the `voterId` could recompute the hash for each candidate and
find out the vote. In production the `pepper` comes from the environment
(`VOTING_RECEIPT_PEPPER`) and never from the repository — the value in `docker-compose.yml` is
for development only.

Even with the `pepper`, the design has a known limit: whoever obtains the server secret can
reconstruct any voter's vote. The next phase should move to a *commitment* with a random nonce
per vote, kept only by the voter. That's why `votes.receipts` deliberately does **not** carry
the candidate — a topic linking receipt to candidate would be a map of every voter's vote.

## Next phases

1. **Proofs served from disk.** Persistence solved startup time, not memory: the trees stay
   whole in RAM. A leaf-to-disk-position index would remove the ceiling on votes per process.
2. **Consistency proof between roots** (`GET /consistency?from=N&to=M`), proving that one tree
   is an extension of the other and that nothing was rewritten in the middle.
3. **Per-window aggregation before publishing.** Today every vote emits a count update — too
   much volume for a real election. The Merkle Tree window already exists and solves this.
4. **Lateness tolerance at closing.** Today the deadline is strict: the stamped `castAt`
   counts, with no slack. A legitimate vote with high latency at the boundary is rejected. The
   slack would be a field in `ElectionSchedule`, and needs to be smaller than the interval until
   the final root is published.

## Implementation notes

- The contracts are `record`s, which don't satisfy Flink's POJO contract. Instead of falling
  back to Kryo (which can't instantiate records), the job uses `JsonTypeInfo`/
  `JsonTypeSerializer`: the same JSON as the topic, also between operators and in state. One
  format to debug, and state that survives adding fields. If the volume makes this expensive,
  the way forward is a binary format in the contracts — not a patch in the serializer.
- The sinks' `transaction.timeout.ms` (900000) must be ≤ the broker's
  `transaction.max.timeout.ms`, otherwise the transactional producer is refused and the job
  doesn't start. Both are pinned in `docker-compose.yml`.
- The Flink image is customized (`infra/flink/Dockerfile`) only so that `/flink-checkpoints`
  belongs to the `flink` user — without it the named volume is created as root and the
  JobManager fails to create the checkpoint.

## Closing the vote

```bash
VOTING_OPENS_AT=2026-10-04T11:00:00Z VOTING_CLOSES_AT=2026-10-04T20:00:00Z make up
make submit JOB_ARGS="--bootstrap.servers kafka:9092 \
  --election.opens.at 2026-10-04T11:00:00Z --election.closes.at 2026-10-04T20:00:00Z"
```

Without these two variables, the election has no deadline — convenient in development, and an
operational mistake in production.

### The vote time belongs to the server

`castAt` **doesn't exist in the HTTP contract**. The server stamps the moment of arrival and
ignores any field the client sends. Sending `"castAt":"2026-10-04T12:00:00Z"` in a POST after the
deadline still returns `403`.

There's no way to know the moment of the click without trusting the device's clock, and trusting
it would make closing bypassable by backdating. The price is that the stamp includes network
latency — a sub-second difference, relevant only at the deadline boundary.

The closing sentinel is stamped at `closesAt + voting.closing-margin-ms` (30s by default), and
**not** at the moment the scheduler wakes up. The scheduler fires at some point within its
interval; inheriting that delay would give the sentinel a different time on every run, and that
time goes into the log — the source from which the roots are reproduced. The margin also needs
to exceed the job's `watermark.out.of.orderness.ms` (5s by default): the watermark is
`latest_time_seen − out_of_orderness`, so a sentinel too close to the closing would leave the
last window unclosed.

### Two layers, one authority

The API rejects with `403 VOTACAO_FECHADA` (voting closed) as a courtesy, so the voter doesn't
get a receipt the tally will discard. **The authority is Flink**, for the same reason it already
is for uniqueness: it's the single point that sees every vote with a single clock.

Verified by injecting a vote straight into Kafka, bypassing the API entirely, with `castAt` five
seconds after the deadline — Flink rejected it as `ELECTION_CLOSED` in `votes.rejected`.

`ELECTION_NOT_OPEN` is a separate reason from `ELECTION_CLOSED`: the two situations call for
different investigations — one suggests a clock running ahead, the other is the normal case of
someone who voted too late.

## The watermark stall, and why heartbeats

This was the subtlest problem in the system, and it's worth recording in full.

Flink's watermark is derived **from the data**: `forBoundedOutOfOrderness(5s)` emits
`latest_castAt_seen - 5s`. Without a new event, there's no new maximum, and it freezes. The
window only fires when the watermark passes its end — which never comes.

Consequence measured before the fix: **a lone vote was never sealed**. Ninety seconds, six
checks, `404` on every one. The voter would never get an inclusion proof. And this happened
precisely at the most critical moment — the tail of an election, right before closing.

`withIdleness(30s)` **doesn't solve it**, despite what the name suggests: it marks a
*partition* as idle so it doesn't hold back the combined watermark of the others. When all of
them are idle, the watermark simply stops.

### The obvious fix would destroy auditability

The reflex is to use `ProcessingTimeoutTrigger`, which fires the window on processing time. It
works — and breaks the Merkle Tree. The content of each window would then depend on the wall
clock during processing: two reprocessings of the same log, on machines of different speeds,
would cut the windows at different points and produce **different roots**. A root that isn't
reproducible proves nothing.

### The fix: time becomes data

`votes.control` doesn't carry votes, it carries time:

- **Heartbeat** every few seconds, with the server's time. Published by the ingest API, because
  that's where the same clock that stamps the votes lives.
- **Closing sentinel**, once, when the deadline passes. It pushes the watermark past the last
  window and gets the final root published.

Both enter the stream joined with the votes, under a single watermark, and are dropped right
after the filter — they did their job just by having existed in the stream with a timestamp.
Since everything stays in event time, replay reproduces the same windows and the same roots.

The heartbeat interval needs to be comfortably smaller than the Merkle Tree window: it's what
makes a window without votes close, and a window that doesn't close is a group of voters without
an inclusion proof.

After the fix, the same lone vote seals in ~45s (15s window + 5s watermark + heartbeat +
checkpoint) and the proof answers `200`.
