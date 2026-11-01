# SimpleDB+ Demo Script

This walks through our changes **in change-log order**: Lab 1 → Lab 7, then the two bonuses.
Each step builds on the previous one.

Read it top to bottom like a play script. Each step alternates between:

- **Say:** the words to say out loud (in quotes; put them in your own words)
- **Command:** what to paste into SimpleIJ, one line at a time
- **Expected output:** what appears on screen

Every step ends with a **Background** box: the classes we changed and the files to open if someone asks to see the code. You don't need to read it out.

Every query and output below was run against a fresh `studentdb` on `main`.

**Time plan (about 20 min with the full plan explanations; skip the number walkthroughs to save time)**

| Step | Topic | ~Time |
|---|---|---|
| 0 | Intro and the dataset | 1.5 min |
| 1 | Lab 1: Non-equality predicates | 1 min |
| 2 | Lab 2: Hash and B+-tree indexes | 3 min |
| 3 | Lab 3: ORDER BY | 1 min |
| 4 | Lab 4: Merge join and nested-loop join (3-way joins) | 4 min |
| 5 | Lab 5A: Hash join | 0.5 min |
| 6 | Lab 5B: Aggregates and GROUP BY | 1.5 min |
| 7 | Lab 6: DISTINCT | 1 min |
| 8 | Lab 6B: EXPLAIN | 2 min |
| 9 | Lab 7: Join experiments | 1 min |
| 10 | Bonus 1: Query error checking | 1.5 min |
| 11 | Bonus 2: DOUBLE type | 1.5 min |
| — | Wrap-up and Q&A | — |

---

## Before the demo (10 minutes early)

1. Eclipse: select both projects → **F5** → **Project → Clean… → Clean all projects**. Check that the Problems view shows no errors.
2. Delete the old `SimpleDBClients/studentdb` folder so the data matches this script.
3. Run `embedded.CreateStudentDB`.
4. Run `SimpleIJ`, type `jdbc:simpledb:studentdb`, and leave it at `SQL>`.

> **Do not run these two queries during the demo:**
> - `explain select eid, sectionid, grade from enroll where studentid = 4` shows `blocks=-2147483645` (B-tree cost bug on tiny tables).
> - `select sname, gradyear, title, yearoffered from student, section, course where courseid = cid and gradyear <= yearoffered order by title` crashes with a `NullPointerException` (MergeJoinScan bug).
>
> Neither fix is in the code yet. See "Known issues" at the bottom.

---

## Step 0: Intro and our database

**Say:**
> "Hi, we're going to show the features we added to SimpleDB, in the order we built them: Labs 1 to 7, then our two bonus features.
>
> First, the data. We're using the standard `studentdb` from `CreateStudentDB`. It has five tables (student, dept, course, section and enroll), and the biggest has 9 rows, so we're under the 100-tuple limit. Let me show you what's in it."

**Command:**
```sql
select sid, sname, majorid, gradyear from student
select did, dname from dept
select cid, title, deptid from course
select sectid, courseid, prof, yearoffered from section
select eid, studentid, sectionid, grade from enroll
```

**Expected output** (one table after each command):
```
STUDENT (9 rows)                             DEPT (3 rows)
 sid sname majorid gradyear                   did dname
   1 joe     10     2021                       10 compsci
   2 amy     20     2020                       20 math
   3 max     10     2022                       30 drama
   4 sue     20     2022
   5 bob     30     2020                     COURSE (6 rows)
   6 kim     20     2020                      cid title       deptid
   7 art     30     2021                       12 db systems    10
   8 pat     20     2019                       22 compilers     10
   9 lee     10     2021                       32 calculus      20
                                               42 algebra       20
SECTION (5 rows)                               52 acting        30
 sectid courseid prof     yearoffered          62 elocution     30
   13     12     turing     2018
   23     12     turing     2019             ENROLL (6 rows)
   33     32     newton     2019              eid studentid sectionid grade
   43     32     einstein   2017               14     1        13      A
   53     62     brando     2018               24     1        43      C
                                               34     2        43      B+
                                               44     4        33      B
                                               54     4        53      A
                                               64     6        53      A
```

**Say:**
> "Students have a major, which links to dept. Courses belong to a dept, sections are offerings of a course, and enroll links students to sections with a grade. There are also two indexes, a hash index on `student.majorid` and a B+-tree on `enroll.studentid`. I'll come back to those in Lab 2.
>
> Quickly, how every query runs: the parser turns the SQL into a QueryData object, our verifier checks it against the catalog, and the heuristic planner builds a tree of plans. Opening that tree gives a tree of scans, and we pull rows from the top. As we go I'll use `explain` to show that plan tree. We built that in Lab 6B."

---

## Step 1 — Lab 1: Non-equality predicates

**Say:**
> "Lab 1. Originally SimpleDB only supported `=` in a WHERE clause. We added `<`, `<=`, `>`, `>=`, `!=` and `<>`. For example, students graduating in 2021 or later:"

**Command:**
```sql
select sname, gradyear from student where gradyear >= 2021
```
**Expected output:**
```
      sname gradyear
--------------------
        joe     2021
        max     2022
        sue     2022
        art     2021
        lee     2021
```

**Say:**
> "Five students. The comparison also works on strings. Here are the names alphabetically before 'c':"

**Command:**
```sql
select sname from student where sname < 'c'
```
**Expected output:** amy, bob, art

**Say:**
> "And not-equal. Every enrollment that isn't an A:"

**Command:**
```sql
select eid, grade from enroll where grade <> 'A'
```
**Expected output:** 24 C, 34 B+, 44 B

**Say:**
> "Here's the plan for the first query:"

**Command:**
```sql
explain select sname, gradyear from student where gradyear >= 2021
```
**Expected output:**
```
ProjectPlan(fields=[sname, gradyear])  [blocks=1, records=4]
  SelectPlan(predicate=gradyear>=2021)  [blocks=1, records=4]
    TablePlan(table=student)  [blocks=1, records=9]
```

**Say:**
> "Since this is the first plan, let me explain how to read one. You read it from the bottom up, and each line feeds its rows into the line above it, which is indented one less.
>
> TablePlan reads the student table. SelectPlan is the WHERE clause, and you can see our `>=` operator in it. ProjectPlan is the SELECT list; it keeps just sname and gradyear.
>
> Every line ends with two numbers in brackets. These are the planner's *estimates*, worked out before anything runs:
> - **blocks** is how many disk blocks the planner expects that part of the plan to read. Disk reads are the main cost in a database, so this is the planner's cost measure.
> - **records** is how many rows it expects that line to pass up to the line above.
>
> The planner gets the starting numbers from the catalog statistics. The student table is 9 rows stored in 1 block, so TablePlan says blocks=1, records=9.
>
> SelectPlan still has blocks=1, because filtering doesn't read anything extra; it just checks the rows coming up from below. But records drops to 4. SimpleDB has no statistics about the actual values, so for any non-equality condition it assumes half the rows match: 9 divided by 2, rounded down, is 4.
>
> ProjectPlan only removes columns, so both numbers stay the same: 1 block, 4 rows.
>
> The real query returned 5 rows, not 4. That's fine, because these numbers are only used to compare plans. The rows themselves are always checked properly, so the result is always correct."

> **Background:** the Lexer gained `matchOpr()`/`eatOpr()` to read multi-character operators. `Term` stores the operator, and `isSatisfied()` uses `Constant.compareTo()`, so ints and strings both work. The non-equality reduction factor is 2.
> **Code:** `parse/Lexer.java`, `query/Term.java`

---

## Step 2 — Lab 2: Hash and B+-tree indexes

**Say:**
> "Lab 2 is indexes. SimpleDB only had hash indexes. We added B+-trees, and you choose the type when you create the index with `using hash` or `using btree`. The type is saved in the index catalog:"

**Command:**
```sql
select indexname, tablename, fieldname, indextype from idxcat
```
**Expected output:**
```
        indexname        tablename        fieldname        indextype
--------------------------------------------------------------------
     majoridindex          student          majorid             hash
   studentidindex           enroll        studentid            btree
```

**Say:**
> "So majorid has a hash index and enroll.studentid has a B+-tree. Let me show the difference an index makes. First, a filter on gradyear, which has no index:"

**Command:**
```sql
explain select sname, majorid, gradyear from student where gradyear = 2020
```
**Expected output:**
```
ProjectPlan(fields=[sname, majorid, gradyear])  [blocks=1, records=2]
  SelectPlan(predicate=gradyear=2020)  [blocks=1, records=2]
    TablePlan(table=student)  [blocks=1, records=9]
```

**Say:**
> "That's a plain scan: read every student and test each one. TablePlan reads the 1 block holding all 9 students.
>
> Because this is an equality, SelectPlan's estimate is different from before. For `field = value`, the planner divides by the number of *distinct values* it thinks the field has. SimpleDB doesn't actually count distinct values; it guesses one third of the rows plus one. For 9 rows that's 1 + 9/3 = 4 distinct gradyears, so it estimates 9 / 4 = 2 rows. In reality 3 students graduated in 2020, so again it's a rough estimate.
>
> Now the same query on majorid, which has the hash index:"

**Command:**
```sql
select sname, majorid, gradyear from student where majorid = 20
```
**Expected output:**
```
index on majorid used
      sname majorid gradyear
----------------------------
        amy      20     2020
        sue      20     2022
        kim      20     2020
        pat      20     2019
```

**Say:**
> "The planner prints 'index on majorid used'. In the plan:"

**Command:**
```sql
explain select sname, majorid, gradyear from student where majorid = 20
```
**Expected output:**
```
ProjectPlan(fields=[sname, majorid, gradyear])  [blocks=2, records=2]
  SelectPlan(predicate=majorid=20)  [blocks=2, records=2]
    IndexSelectPlan(value=20)  [blocks=2, records=2]
      TablePlan(table=student)  [blocks=1, records=9]
```

**Say:**
> "There's now an IndexSelectPlan instead of a scan. It hashes 20 to one of 100 buckets, gets the matching record IDs, and jumps straight to those rows.
>
> The TablePlan under it isn't a full scan any more. It's just the table the index points into, which is why it still shows the table's size, 1 block and 9 rows.
>
> IndexSelectPlan's numbers work like this. records is 2: the same guess as before, 9 rows divided by 4 distinct values. blocks is the cost of searching the index plus one block for each matching row. Searching a hash index costs its size divided by 100 buckets, and our index is far smaller than one block, so that part rounds down to 0. That leaves 0 + 2 rows = 2 blocks.
>
> SelectPlan above it re-checks `majorid = 20` but adds no cost, so the numbers carry up unchanged: 2 blocks, 2 rows.
>
> You might notice 2 blocks for the index is *more* than the 1 block for the full scan. That's because the whole student table fits in one block, so a scan is as cheap as it gets. The heuristic planner always uses an index for `field = constant`, because on a real-sized table a scan reads every block, while the index reads only the matching rows.
>
> The B+-tree works the same way for lookups. Here are student 4's enrollments:"

**Command** (do **not** run `explain` on this one):
```sql
select eid, sectionid, grade from enroll where studentid = 4
```
**Expected output:**
```
index on studentid used
    eid sectionid grade
-----------------------
     54        53     A
     44        33     B
```

**Say:**
> "That lookup walks from the root of the B+-tree down to the leaf holding studentid 4. Indexes are also used in joins. Here are the grades of students graduating in 2022:"

**Command:**
```sql
select sname, grade from student, enroll where sid = studentid and gradyear = 2022
```
**Expected output:** sue A, sue B

**Command:**
```sql
explain select sname, grade from student, enroll where sid = studentid and gradyear = 2022
```
**Expected output:**
```
ProjectPlan(fields=[sname, grade])  [blocks=7, records=1]
  SelectPlan(predicate=sid=studentid)  [blocks=7, records=1]
    IndexJoinPlan(field=sid)  [blocks=7, records=4]
      SelectPlan(predicate=gradyear=2022)  [blocks=1, records=2]
        TablePlan(table=student)  [blocks=1, records=9]
      TablePlan(table=enroll)  [blocks=1, records=6]
```

**Say:**
> "That's an IndexJoinPlan. Its first child is the outer side: the students, filtered to gradyear 2022, estimated at 2 of the 9. Its second child, enroll, is the inner side, which is never scanned. For each outer student, the join looks up their enrollments through the B+-tree on studentid.
>
> Following the records upwards: 2 students are expected at the bottom. Each student is expected to match 2 enrollments (6 enrollment rows divided by 1 + 6/3 = 3 distinct studentids), so IndexJoinPlan estimates 2 × 2 = 4 rows.
>
> The SelectPlan above re-applies `sid = studentid`. The planner divides by the number of distinct values *again*, so the estimate drops to 1, even though the real answer is 2 rows for sue. Counting the join condition twice makes the estimate too low, but it doesn't change the result."

> **Note (not to say):** don't explain the `blocks=7` on these lines. It's not a real estimate. It comes from known issue #2 at the bottom: the B-tree lookup cost is about −2 billion on this tiny index, and multiplying it by 2 lookups overflows back around to a small number. If you're asked, talk about the records numbers, or say the B-tree cost formula breaks down for an index smaller than one block.

> **Background:** the parser accepts `using hash | btree` (hash is the default) and stores the type in `CreateIndexData`. `idxcat` gained an `indextype` column, and `IndexInfo` opens the matching index class and uses its cost formula. We also switched SimpleDB to the `HeuristicQueryPlanner` + `IndexUpdatePlanner`, which use indexes.
> **Code:** `metadata/IndexInfo.java`, `metadata/IndexMgr.java`, `parse/Parser.java`, `embedded/CreateStudentDB.java`

---

## Step 3 — Lab 3: ORDER BY

**Say:**
> "Lab 3 is ORDER BY, with any number of fields, each ascending or descending. Here are the students sorted by major ascending, and within each major, newest graduation year first:"

**Command:**
```sql
select sname, majorid, gradyear from student order by majorid, gradyear desc
```
**Expected output:**
```
      sname majorid gradyear
----------------------------
        max      10     2022
        lee      10     2021
        joe      10     2021
        sue      20     2022
        kim      20     2020
        amy      20     2020
        pat      20     2019
        art      30     2021
        bob      30     2020
```

**Say:**
> "Majors go 10, 20, 30, and inside each one the years go down. In the plan:"

**Command:**
```sql
explain select sname, majorid, gradyear from student order by majorid, gradyear desc
```
**Expected output:**
```
ProjectPlan(fields=[sname, majorid, gradyear])  [blocks=1, records=9]
  SortPlan(fields=[majorid, gradyear], directions=[true, false])  [blocks=1, records=9]
    TablePlan(table=student)  [blocks=1, records=9]
```

**Say:**
> "The SortPlan has a list of fields and a matching list of directions: true is ascending, false is descending. The comparator goes field by field and just flips the result for descending fields.
>
> On the numbers: sorting doesn't add or remove rows, so records stays 9 all the way up. For a sort, blocks means the size of the sorted temporary table that the rows are read back from. 9 student rows fit in 1 block, so it's 1. The one-time cost of doing the sort isn't counted on this line. The planner works that out separately when it needs it, for example when comparing merge join with hash join, which we'll see next."

> **Background:** `order`, `by`, `asc` and `desc` are keywords. `Parser.orderList()` collects the fields and directions into `QueryData`. `SortPlan` takes the directions, and `RecordComparator.compare()` flips the sign for DESC.
> **Code:** `parse/Parser.java`, `materialize/SortPlan.java`, `materialize/RecordComparator.java`

---

## Step 4 — Lab 4: Join algorithms on 3-way joins

**Say:**
> "Lab 4 is join algorithms. Originally, a join without an index was a cross product followed by a filter. Now the planner tries an index join first, then a sort-merge join, then a nested-loop join. Let's join three tables, student, enroll and section, to get each student's professor and grade, sorted by name, with grades descending within a name:"

**Command:**
```sql
select sname, prof, grade from student, enroll, section where sid = studentid and sectionid = sectid order by sname asc, grade desc
```
**Expected output:**
```
mergejoin on sectid=sectionid used (est. cost 6 vs hashjoin 6)
mergejoin on studentid=sid used (est. cost 7 vs hashjoin 7)
      sname     prof grade
--------------------------
        amy einstein    B+
        joe einstein     C
        joe   turing     A
        kim   brando     A
        sue   newton     B
        sue   brando     A
```

**Say:**
> "The planner tells us it used merge join for both joins. Here's the plan:"

**Command:**
```sql
explain select sname, prof, grade from student, enroll, section where sid = studentid and sectionid = sectid order by sname asc, grade desc
```
**Expected output:**
```
ProjectPlan(fields=[sname, prof, grade])  [blocks=1, records=1]
  SortPlan(fields=[sname, grade], directions=[true, false])  [blocks=1, records=1]
    SelectPlan(predicate=sid=studentid)  [blocks=2, records=1]
      MergeJoinPlan(fields=studentid=sid)  [blocks=2, records=6]
        SelectPlan(predicate=sectionid=sectid)  [blocks=2, records=3]
          MergeJoinPlan(fields=sectid=sectionid)  [blocks=2, records=10]
            TablePlan(table=section)  [blocks=1, records=5]
            TablePlan(table=enroll)  [blocks=1, records=6]
        TablePlan(table=student)  [blocks=1, records=9]
```

**Say:**
> "Reading bottom up: the planner starts with the smallest table, section, joins in enroll, then student. There's no index on these join fields, so it uses sort-merge join. It sorts both sides on the join key and walks them together, like merging two sorted lists. The ORDER BY sort is on top.
>
> Let me walk through the numbers.
>
> At the bottom, section is 5 rows and enroll is 6 rows, 1 block each.
>
> The first MergeJoinPlan shows blocks=2. A merge join reads both of its sorted inputs once, and each sorted input fits in 1 block, so 1 + 1 = 2. Its records is 10. The join-size formula multiplies the two inputs, 5 × 6 = 30, then divides by the larger number of distinct join values. The guess is 1 + 5/3 = 2 for section and 1 + 6/3 = 3 for enroll, so 30 / 3 = 10.
>
> The SelectPlan above it re-applies the join condition, which divides by 3 again: 10 / 3 = 3. As in Lab 2, the join condition is counted twice, so the estimates from here up are too low.
>
> The second MergeJoinPlan joins those 3 rows with the 9 students. Again it reads two 1-block sorted inputs, so 2 blocks. Records is 3 × 9 / 4 = 6, where 4 is the guess for distinct student ids. The SelectPlan above divides by 4 again, giving 1.
>
> SortPlan and ProjectPlan don't change the row count, so the top says 1 row. The real answer is 6 rows. Again, the result is correct; only the estimate is low.
>
> Remember the message 'est. cost 6 vs hashjoin 6' from when we ran the query? That's the planner pricing both algorithms for the first join, and this time the one-time sorting cost *is* included. Merge join: sorting section costs 1 block to read plus 1 to write, the same for enroll, then 2 blocks to merge, so 2 + 2 + 2 = 6. Hash join: partitioning reads both tables and writes them out, 4 blocks, then joining the partitions reads 2, so 4 + 2 = 6. It's a tie, and merge join wins ties.
>
> We can also mix in non-equality filters. Here are the same joins, only for sections before 2019:"

**Command:**
```sql
select sname, prof, yearoffered, grade from student, enroll, section where sid = studentid and sectionid = sectid and yearoffered < 2019 order by yearoffered desc, sname
```
**Expected output:**
```
      sname     prof yearoffered grade
--------------------------------------
        joe   turing        2018     A
        kim   brando        2018     A
        sue   brando        2018     A
        amy einstein        2017    B+
        joe einstein        2017     C
```

**Command:**
```sql
explain select sname, prof, yearoffered, grade from student, enroll, section where sid = studentid and sectionid = sectid and yearoffered < 2019 order by yearoffered desc, sname
```
**Expected output:**
```
ProjectPlan(fields=[sname, prof, yearoffered, grade])  [blocks=0, records=0]
  SortPlan(fields=[yearoffered, sname], directions=[false, true])  [blocks=0, records=0]
    SelectPlan(predicate=sid=studentid)  [blocks=2, records=0]
      MergeJoinPlan(fields=studentid=sid)  [blocks=2, records=2]
        SelectPlan(predicate=sectionid=sectid)  [blocks=2, records=1]
          MergeJoinPlan(fields=sectid=sectionid)  [blocks=2, records=4]
            SelectPlan(predicate=yearoffered<2019)  [blocks=1, records=2]
              TablePlan(table=section)  [blocks=1, records=5]
            TablePlan(table=enroll)  [blocks=1, records=6]
        TablePlan(table=student)  [blocks=1, records=9]
```

**Say:**
> "Look at the bottom: the `yearoffered < 2019` filter sits right on the section table, *below* the join. A filter that only mentions one table is applied before the join, so fewer rows go into it.
>
> You can see that in the numbers. Section's 5 rows become an estimated 2 after the filter (half, since it's a non-equality). So the first join now expects 2 × 6 / 3 = 4 rows, compared with 10 in the previous query.
>
> Going up, the same double-counting happens: 4 / 3 = 1, then 1 × 9 / 4 = 2, then 2 / 4 = 0. The top of the plan says 0 rows, but the query really returns 5. When the estimate is 0 rows, the sorted temporary table is estimated at 0 blocks, which is why SortPlan and ProjectPlan show blocks=0. It's an estimate problem, not an execution problem.
>
> Finally, what if the join condition itself isn't equality? Here we match drama students (major 30) with every section offered before they graduated:"

**Command:**
```sql
select sname, gradyear, title, yearoffered from student, section, course where courseid = cid and gradyear > yearoffered and majorid = 30 order by sname, yearoffered desc
```
**Expected output:**
```
index on majorid used
nestedloopjoin used
mergejoin on courseid=cid used (est. cost 9 vs hashjoin 9)
      sname gradyear                title yearoffered
-----------------------------------------------------
        art     2021             calculus        2019
        art     2021           db systems        2019
        art     2021            elocution        2018
        art     2021           db systems        2018
        art     2021             calculus        2017
        bob     2020             calculus        2019
        bob     2020           db systems        2019
        bob     2020            elocution        2018
        bob     2020           db systems        2018
        bob     2020             calculus        2017
```

**Say:**
> "`gradyear > yearoffered` is a join between student and section, but it's not an equality, and index, hash and merge joins all need equality. So the planner falls back to a nested-loop join. For each student it rescans the sections and tests the condition, so it works with any operator. This one query uses three techniques: the hash index for major 30, a nested-loop join, and a merge join for courseid = cid. I'll show its plan when we get to EXPLAIN."

> **Background:** `TablePlanner.makeJoinPlan()` tries index join → equality join (merge, plus hash from Lab 5A) → nested loop. `NestedLoopJoinPlan`/`NestedLoopJoinScan` are new and test the predicate inline. We also fixed `RecordComparator` (ascending fields weren't sorted), fixed `Constant.equals()` throwing on null (it crashed `MergeJoinScan`), and added the ORDER BY step to `HeuristicQueryPlanner`.
> **Code:** `opt/TablePlanner.java`, `materialize/MergeJoinScan.java`, `query/NestedLoopJoinScan.java`

---

## Step 5 — Lab 5A: Partition-based hash join

**Say** (no command; point at the messages from Step 4 still on screen):
> "Lab 5A added hash join. You might have noticed this line earlier: 'mergejoin used, estimated cost 6 versus hashjoin 6'. For every equality join without an index, the planner builds both a hash join and a merge join, estimates both costs, and picks the cheaper one.
>
> Hash join splits both inputs into partitions by hashing the join value, so matching rows always land in the same partition. Then, partition by partition, it loads the smaller side into an in-memory hash table and streams the other side through it.
>
> On these one-block tables the costs tie, and hash join has to be strictly cheaper to win, so we see merge join. On the large dataset from Lab 7, the planner picks hash join by itself. I'll show those numbers in a minute."

> **Background:** `HashJoinPlan.open()` partitions both inputs into (available buffers − 1) temp tables and uses the smaller one as the build side. `partitionCost()` is kept separate from `blocksAccessed()` so the costs can be compared. `TablePlanner.makeEquiJoin()` picks hash or merge, and `sortCost()` estimates the merge join's sort. We also fixed `SortScan.savePosition()/restorePosition()`, which skipped rows when merge join went back over duplicate keys.
> **Code:** `materialize/HashJoinPlan.java`, `materialize/HashJoinScan.java`, `opt/TablePlanner.java`

---

## Step 6 — Lab 5B: Aggregates, with and without GROUP BY

**Say:**
> "Lab 5B is aggregates: count, sum, avg, min and max. Without a GROUP BY, the whole table is one group:"

**Command:**
```sql
select count(sid), min(gradyear), max(gradyear), avg(gradyear) from student
```
**Expected output:**
```
 countofsid minofgradyear maxofgradyear avgofgradyear
-----------------------------------------------------
          9          2019          2022     2020.6667
```

**Say:**
> "Nine students, graduating between 2019 and 2022, average 2020.67. Each column gets a name like `countofsid`, which we'll use in a moment. MIN and MAX also work on strings:"

**Command:**
```sql
select count(eid), min(grade), max(grade) from enroll
```
**Expected output:** `6  A  C`

**Say:**
> "Here's the plan for the first one:"

**Command:**
```sql
explain select count(sid), min(gradyear), max(gradyear), avg(gradyear) from student
```
**Expected output:**
```
ProjectPlan(fields=[countofsid, minofgradyear, maxofgradyear, avgofgradyear])  [blocks=1, records=1]
  GroupByPlan(fields=[])  [blocks=1, records=1]
    TablePlan(table=student)  [blocks=1, records=9]
```

**Say:**
> "GroupByPlan has no group fields, `fields=[]`, and it sits directly on the table, with no sort in between. It reads the 1 block of students, 9 rows, and outputs exactly 1 row. The estimate of records=1 is exact here: with no GROUP BY, there's always one output row."

**Say:**
> "Now with GROUP BY, the same statistics per major:"

**Command:**
```sql
select majorid, count(sid), min(gradyear), max(gradyear), avg(gradyear) from student group by majorid
```
**Expected output:**
```
 majorid countofsid minofgradyear maxofgradyear avgofgradyear
-------------------------------------------------------------
      10          3          2021          2022     2021.3333
      20          4          2019          2022       2020.25
      30          2          2020          2021        2020.5
```

**Say:**
> "One row per major. Internally, the rows are sorted on majorid so each group's rows sit next to each other. The group-by scan then walks one group at a time, updating every aggregate, and outputs a row when the group changes. (If you run `explain` on this, GroupByPlan shows records=4: the planner estimates one row per distinct majorid, using its usual guess of 1 + 9/3 = 4. The real answer is 3.)
>
> It also works on top of a join, and you can sort by the aggregate using its output name. Here are the departments ranked by number of students:"

**Command:**
```sql
select dname, count(sid) from student, dept where majorid = did group by dname order by countofsid desc
```
**Expected output:**
```
    dname countofsid
--------------------
     math          4
  compsci          3
    drama          2
```

**Say:**
> "And here's how a plan like that is built, for professors ranked by number of enrollments:"

**Command:**
```sql
explain select prof, count(eid) from section, enroll where sectid = sectionid group by prof order by countofeid desc, prof
```
**Expected output:**
```
mergejoin on sectid=sectionid used (est. cost 6 vs hashjoin 6)
ProjectPlan(fields=[prof, countofeid])  [blocks=1, records=2]
  SortPlan(fields=[countofeid, prof], directions=[false, true])  [blocks=1, records=2]
    GroupByPlan(fields=[prof])  [blocks=1, records=2]
      SelectPlan(predicate=sectid=sectionid)  [blocks=2, records=3]
        MergeJoinPlan(fields=sectid=sectionid)  [blocks=2, records=10]
          TablePlan(table=section)  [blocks=1, records=5]
          TablePlan(table=enroll)  [blocks=1, records=6]
```

**Say:**
> "Join first, then group by professor, then sort by the count descending, with ties broken by name.
>
> The bottom half is the same join we saw in Lab 4: merge join, 2 blocks, estimated 10 rows, then 3 after the condition is re-applied.
>
> GroupByPlan's blocks is 1. Before grouping, the 3 joined rows are sorted on prof into a temporary table, which fits in 1 block, and GroupByPlan reads that. Its records is the estimated number of groups, one per distinct professor. Section has 5 rows, so the guess is 1 + 5/3 = 2 professors. There are really 4 (brando, einstein, newton and turing).
>
> The SortPlan for ORDER BY sorts those 2 estimated rows, which also fit in 1 block. ProjectPlan carries the same numbers to the top."

> **Background:** new keywords `group`, `sum`, `count`, `avg`, `min` and `max`. The parser stores aggregates by output name, and `checkGrouping()` rejects plain fields that are neither grouped nor aggregated. `GroupByPlan` sorts on the group fields; with no group fields it skips the sort and treats the input as one group. Each aggregate sets its own output type, so MIN/MAX keep the field's type.
> **Code:** `parse/Parser.java`, `materialize/GroupByPlan.java`, `materialize/GroupByScan.java`, `materialize/AvgFn.java`

---

## Step 7 — Lab 6: DISTINCT

**Say:**
> "Lab 6 is DISTINCT. Joins often produce duplicates. Here's each student's department name:"

**Command:**
```sql
select dname from dept, student where did = majorid
```
**Expected output:** 9 rows: compsci ×3, math ×4, drama ×2

**Say:**
> "One row per student, so the names repeat. Adding DISTINCT:"

**Command:**
```sql
select distinct dname from dept, student where did = majorid
```
**Expected output:**
```
    dname
---------
  compsci
     math
    drama
```

**Command:**
```sql
explain select distinct dname from dept, student where did = majorid
```
**Expected output:**
```
DistinctPlan  [blocks=7, records=1]
  ProjectPlan(fields=[dname])  [blocks=7, records=1]
    SelectPlan(predicate=did=majorid)  [blocks=7, records=1]
      IndexJoinPlan(field=did)  [blocks=7, records=6]
        TablePlan(table=dept)  [blocks=1, records=3]
        TablePlan(table=student)  [blocks=1, records=9]
```

**Say:**
> "DistinctPlan sits on top of the projection, because duplicates only appear once the other columns are dropped. It keeps a hash set of rows it has already returned and skips repeats. So it's one pass with no sorting, which is why the output stays in its original order (compsci, math, drama) rather than alphabetical.
>
> This plan is also a good example of how an index join is costed, this time through the *hash* index on student.majorid. dept is the outer side: 3 rows in 1 block. For each department, the join looks up matching students in the hash index.
>
> IndexJoinPlan's blocks is 7, made of three parts. First, 1 block to read dept. Second, the cost of 3 index lookups, one per department; each costs the index size divided by 100 buckets, which rounds down to 0 for an index this small. Third, 1 block for each matching student row fetched, an estimated 6. So 1 + 0 + 6 = 7.
>
> The 6 records comes from 3 departments × 2 students expected per department, using the usual guess of 9 / (1 + 9/3) = 2.
>
> The SelectPlan above re-applies `did = majorid` and divides by 4 distinct values, so the estimate drops to 1. ProjectPlan keeps it at 1. DistinctPlan can never output more rows than it gets, so its estimate stays at 1.
>
> None of these steps reads extra blocks, so blocks stays 7 all the way to the top. Really there are 3 distinct department names."

> **Background:** `distinct` is a keyword, and `QueryData.isDistinct()` stores it. All three planners wrap the final projection in a `DistinctPlan`, and `DistinctScan` uses a HashSet. The estimated output is min(input rows, product of each field's distinct values).
> **Code:** `plan/DistinctPlan.java`, `query/DistinctScan.java`

---

## Step 8 — Lab 6B: EXPLAIN

**Say:**
> "Lab 6B is the EXPLAIN command we've been using all along. When SimpleIJ sees a line starting with `explain`, the planner builds exactly the plan the query would use, but doesn't run it. A plan printer walks the tree, indents each level, and prints each operator with its estimated blocks and rows. It works over both the embedded and the network connection.
>
> Here's the plan for that drama-student query from Lab 4:"

**Command:**
```sql
explain select sname, gradyear, title, yearoffered from student, section, course where courseid = cid and gradyear > yearoffered and majorid = 30 order by sname, yearoffered desc
```
**Expected output:**
```
index on majorid used
nestedloopjoin used
mergejoin on courseid=cid used (est. cost 9 vs hashjoin 9)
ProjectPlan(fields=[sname, gradyear, title, yearoffered])  [blocks=1, records=3]
  SortPlan(fields=[sname, yearoffered], directions=[true, false])  [blocks=1, records=3]
    SelectPlan(predicate=courseid=cid)  [blocks=2, records=3]
      MergeJoinPlan(fields=courseid=cid)  [blocks=2, records=10]                      <- equality join
        NestedLoopJoinPlan(predicate=gradyear>yearoffered)  [blocks=4, records=5]     <- non-equality join
          SelectPlan(predicate=majorid=30)  [blocks=2, records=2]
            IndexSelectPlan(value=30)  [blocks=2, records=2]                          <- hash index
              TablePlan(table=student)  [blocks=1, records=9]
          TablePlan(table=section)  [blocks=1, records=5]
        TablePlan(table=course)  [blocks=1, records=6]
```
(The `<-` labels are ours, not part of the output.)

**Say:**
> "From the bottom: the hash index finds the drama students, a nested-loop join handles the inequality with section, a merge join adds course, and the sort on top handles the ORDER BY. The sorts that merge join and GROUP BY use internally are hidden, so the only SortPlan you see is the ORDER BY.
>
> Let me walk through the numbers once more, because they show how each operator is costed.
>
> The IndexSelectPlan for major 30 is the same as in Lab 2: an estimated 2 students, costing 0 blocks for the hash lookup plus 1 block per student, so 2 blocks.
>
> The NestedLoopJoinPlan's cost is the interesting one: blocks=4. A nested-loop join reads its outer input once, which is 2 blocks. Then, for *every* outer row, it rescans the whole inner input, section, which is 1 block. So 2 + (2 rows × 1 block) = 4. That's why nested loop is the slowest choice on big tables: the cost grows with outer rows times inner size. Its records is 2 students × 5 sections = 10 combinations, halved because `>` is a non-equality, so 5.
>
> The MergeJoinPlan then joins those 5 rows with course. It reads two sorted inputs of 1 block each, so 2 blocks. Records is 5 × 6 / 3 = 10. The SelectPlan above re-applies `courseid = cid`, which divides by 3 again, giving 3. The ORDER BY SortPlan holds those 3 rows in 1 block.
>
> The query really returns 10 rows, so again the estimate is low because the join condition is counted twice, but the result is exactly right.
>
> You can also see the planner printing its decisions above the plan, including 'est. cost 9 vs hashjoin 9' for the merge join. That's the same tie as before, with slightly bigger inputs."

> **Background:** `Plan` gained default `children()` and `description()` methods, implemented by every plan class. `PlanPrinter` formats the tree with `[blocks=…, records=…]` from `blocksAccessed()` and `recordsOutput()`. `Planner.explainQueryPlan()` builds without executing. `PlanStatement.explain()` is implemented for embedded and network JDBC.
> **Code:** `plan/PlanPrinter.java`, `plan/Planner.java`, `SimpleDBClients/src/SimpleIJ.java`

---

## Step 9 — Lab 7: Join experiments (talk only, don't run live)

**Say:**
> "In Lab 7 we compared the join algorithms on real timings. Our tables are one block each, which is too small to time, so we wrote a generator for a bigger database: 2,000 students, 8,000 enrollments, 400 sections and 100 courses, from a fixed random seed so it's reproducible. We also added a switch that restricts which join methods the planner may use, so we can force the same query through each algorithm. Each query ran three times. Here are the averages:"

**Show this table** (on a slide or this doc):

| Query | index | hash | merge | nested loop |
|---|---|---|---|---|
| 2-table (student ⋈ enroll) | **36 ms** | 384 ms | 5793 ms | 6351 ms |
| 4-table (student ⋈ enroll ⋈ section ⋈ course) | 1610 ms\* | **1567 ms** | 18240 ms | 8493 ms |

\* With only index join allowed, 2 of the 3 joins fall back to nested loop, because there's no index on their inner side.

**Say:**
> "Index join wins when there's an index on the join field. Hash join is the best general-purpose equality join. Merge join was the slowest here, because writing and merging all the sorted temp tables dominates its cost. This database is over the 100-tuple limit, so we're not running it live.
>
> Running these experiments also exposed two bugs we fixed: B-tree page splits never released their buffers, and the file manager kept every temp file open until it ran out of file handles."

> **Background:** `opt/JoinMethods` holds the allowed methods (all by default). `TablePlanner` only tries allowed ones and falls back to nested loop with a message. `experiment/CreateExperimentDB` builds `expdb` with B-tree indexes on sid, studentid, sectid and cid. `experiment/JoinExperiment` runs the two queries once per method. Fixes: `BTPage.appendNew()` and `BTreeIndex` unpin their blocks, and `FileMgr` keeps at most 500 files open (LRU).
> **Code:** `opt/JoinMethods.java`, `experiment/JoinExperiment.java`, `file/FileMgr.java`

---

## Step 10 — Bonus 1: Query error checking

**Say:**
> "Now our two bonuses. The first is error checking. In the original SimpleDB, the verify methods were empty, so bad statements went straight through. A misspelled table crashed with a NullPointerException, a condition on a misspelled field was silently ignored, and a string that was too long overwrote the next record on disk. We added a verifier that checks every statement against the catalog before it's planned. For example, a typo in the table name:"

**Command:**
```sql
select sname from studnt
```
**Expected output:**
```
transaction 31 rolled back
SQL Exception: simpledb.plan.SemanticException: table studnt does not exist
```
(The transaction number will vary.)

**Say:**
> "A clear message instead of a crash. This next one is the important one. `gpa` doesn't exist. Before, this returned *every* student without any error:"

**Command:**
```sql
select sname from student where gpa = 3
```
**Expected output:** `field gpa does not exist in student`

**Say:**
> "Types are checked too, both in comparisons and in inserts:"

**Command:**
```sql
select sname from student where sname = 10
```
**Expected output:** `cannot compare sname (varchar) with 10 (int)`

**Command:**
```sql
insert into dept(did, dname) values (40, 'engineering')
```
**Expected output:** `value 'engineering' is too long for field dname (varchar(8))`

**Say:**
> "That one used to silently corrupt the next record. And the GROUP BY rule from Lab 5B:"

**Command:**
```sql
select sname, count(sid) from student
```
**Expected output:** `field sname must appear in the group by clause or be aggregated`

**Say:**
> "Every failure rolls back the transaction, so nothing is half-done. The dept table still has just its three rows:"

**Command:**
```sql
select count(did) from dept
```
**Expected output:** `3`

**Extra examples, if you have time or are asked:**

| Command | Expected output |
|---|---|
| `select sname from student, student` | table student appears more than once in the from clause |
| `insert into dept(did, dname) values ('x', 'art')` | cannot store 'x' (varchar) in field did (int) |
| `insert into dept(did) values (50, 'art')` | insert into dept lists 1 fields but 2 values |
| `update student set sname = gradyear where sid = 2` | cannot set sname (varchar) to gradyear (int) |
| `select sum(sname) from student` | cannot sum non-numeric field sname |
| `create index majoridindex on student(majorid)` | field majorid of student is already indexed |

> **Background:** `QueryVerifier` runs inside `Planner` after parsing and before planning, for every query and update. It checks tables, fields (they must exist in exactly one FROM table), comparison types, SUM/AVG on numeric fields only, insert/update value types and varchar lengths, and CREATE name lengths, duplicate fields and duplicate indexes. A failure throws `SemanticException`.
> **Code:** `plan/QueryVerifier.java`, `plan/Planner.java`, `plan/SemanticException.java`

---

## Step 11 — Bonus 2: DOUBLE data type

**Say:**
> "Our second bonus is a new data type, DOUBLE, alongside int and varchar. Let's make a product table with a double price:"

**Command** (one line at a time):
```sql
create table product(pid int, pname varchar(10), price double)
insert into product(pid, pname, price) values (1, 'pen', 1.25)
insert into product(pid, pname, price) values (2, 'notebook', 4.5)
insert into product(pid, pname, price) values (3, 'backpack', 39.99)
insert into product(pid, pname, price) values (4, 'mug', 8)
```
**Expected output:** `0 records processed` for the create, then `1 records processed` for each insert.

**Say:**
> "Notice the mug's price is the integer 8. An int is allowed in a double column. Filtering and sorting on decimals:"

**Command:**
```sql
select pname, price from product where price > 4.25 order by price desc
```
**Expected output:**
```
      pname      price
----------------------
   backpack      39.99
        mug        8.0
   notebook        4.5
```

**Say:**
> "The mug was stored as 8.0. Ints and doubles are compared by value, so searching for the integer 8 finds it:"

**Command:**
```sql
select pname, price from product where price = 8
```
**Expected output:** mug 8.0

**Say:**
> "Aggregates. SUM of a double column is a double, and AVG now always returns a double:"

**Command:**
```sql
select sum(price), avg(price), min(price), max(price) from product
```
**Expected output:**
```
 sumofprice avgofprice minofprice maxofprice
--------------------------------------------
      53.74     13.435       1.25      39.99
```

**Say:**
> "Updates work too. Updates are logged, so a rollback restores the old double value:"

**Command:**
```sql
update product set price = 9.75 where pid = 4
select pname, price from product where price = 9.75
```
**Expected output:** `1 records processed`, then mug 9.75

**Say:**
> "And the type rule from the verifier: you can't put a double into an int column, because the fraction would be lost:"

**Command:**
```sql
insert into student(sid, sname, majorid, gradyear) values (99, 'zed', 10, 2020.5)
```
**Expected output:** `cannot store 2020.5 (double) in field gradyear (int)`

**Say:**
> "So doubles work everywhere ints do: storage, comparisons, sorting, aggregates, indexes and joins. And because 8 and 8.0 hash the same, they also match in hash indexes and hash joins."

> **Background:** each double takes 8 bytes (`Layout`, `Page.getDouble/setDouble`). The lexer reads `39.99` as one token. `Constant` holds doubles, compares int and double by value, and gives 8 and 8.0 the same hash. `SetDoubleRecord` logs updates for rollback and recovery. `SumFn` and `AvgFn` return doubles. JDBC `getDouble()` works in both drivers, and SimpleIJ rounds to 4 decimal places.
> **Code:** `record/Layout.java`, `file/Page.java`, `parse/Lexer.java`, `query/Constant.java`, `tx/recovery/SetDoubleRecord.java`

---

## Wrap-up

**Say:**
> "To summarise: we added non-equality predicates; hash and B+-tree indexes, used for both selections and joins; multi-field ORDER BY; three join algorithms (sort-merge, nested-loop and hash), chosen by estimated cost; aggregates with and without GROUP BY; DISTINCT; and EXPLAIN. We ran timed join experiments on a larger database, and for bonuses we added semantic error checking and a DOUBLE type. Happy to take questions."

---

## Likely questions

- **Why merge join instead of hash join on studentdb?** With no index, the planner costs both. On 1-block tables they tie (6 vs 6), and hash join only wins when strictly cheaper. On expdb it picks hash join.
- **How is the join order chosen?** Start with the table that has the fewest rows after its filters, then repeatedly add the table that gives the smallest join result (a left-deep tree).
- **The index plan estimates 2 blocks and the scan 1. Why use the index?** student fits in one block, so a scan is already 1. The heuristic always uses an index for `field = constant`, because on bigger tables the scan grows with the table while the index stays at about one lookup.
- **Why doesn't plain `student, enroll` use the B-tree?** Without the gradyear filter, enroll (6) is smaller than student (9), so enroll goes on the outside. The index is on enroll, so it can't be used for lookups.
- **Why does `gradyear >= 2021` estimate 4 rows when there are 5? Is it always half?** Yes. For any non-equality condition, `Term.reductionFactor()` returns a fixed factor of 2, so the planner estimates half the rows (9 / 2 = 4, integer division). It doesn't look at the value or the operator: `> 2021` (actually 2 rows), `<= 2019` (1) and `!= 2020` (6) are all estimated as 4. Several conditions multiply (two conditions → divide by 4). SimpleDB keeps no histograms or min/max statistics, so it can't know how many values fall in a range, and half is a simple rule of thumb. It only affects the estimate used to compare plans. The results are always correct, because every row is actually checked.
- **Why do some EXPLAIN nodes show records=0?** The join predicate is applied again as a SelectPlan above the join, so the estimate is divided twice. The actual results are correct; only the estimate is off.
- **Where do the distinct-value guesses come from?** SimpleDB's statistics manager doesn't count distinct values. It assumes every field has 1 + (rows / 3) distinct values: 4 for student, 3 for enroll and course, 2 for dept and section. Every equality estimate in the plans divides by these.
- **Cheat sheet: what blocks and records mean for each operator**

  | Operator | blocks | records |
  |---|---|---|
  | TablePlan | blocks the table occupies (from catalog stats) | rows in the table |
  | SelectPlan | same as its child (filtering reads nothing extra) | child ÷ 2 for non-equality; child ÷ distinct values for equality |
  | ProjectPlan | same as child | same as child |
  | IndexSelectPlan | index search cost + 1 block per matching row | rows ÷ distinct values |
  | IndexJoinPlan | outer blocks + (outer rows × index search cost) + matching rows | outer rows × matches per lookup |
  | MergeJoinPlan | size of the two sorted inputs (sort cost not included) | left × right ÷ max distinct join values |
  | NestedLoopJoinPlan | outer blocks + (outer rows × inner blocks) | left × right ÷ reduction factor |
  | SortPlan | size of the sorted temp table | same as child |
  | GroupByPlan | blocks of its (sorted) input | product of distinct values of the group fields (1 if no GROUP BY) |
  | DistinctPlan | same as child | min(child rows, product of distinct values of the columns) |

  Hash index search cost = index blocks ÷ 100 buckets. B-tree search cost = 1 + log(index blocks) ÷ log(entries per block).
- **Why must an index exist before inserting rows?** Index entries are only added on insert (`IndexUpdatePlanner`). There's no bulk build from existing rows.
- **Hash vs B-tree?** Hash: one bucket per lookup, equality only. B-tree: root-to-leaf traversal. It could do ranges, but our planner only uses indexes for equality.
- **Why no `student.sid`?** SimpleDB has no table qualifiers. A field name that appears in two FROM tables is reported as ambiguous by the verifier.
- **Bugs you fixed?** RecordComparator didn't sort ascending fields (Lab 4). SortScan's save/restore position skipped rows on duplicate merge-join keys (Lab 5A). B-tree buffer leaks and too many open files on expdb (Lab 7).

---

## Known issues (not yet fixed in the code)

1. **MergeJoinScan crash:** `beforeFirst()` doesn't reset `joinval`, so a merge join that gets restarted (for example, under a nested-loop join) crashes. Repro: the `gradyear <= yearoffered order by title` query in the warning box at the top.
2. **B-tree cost on tiny tables:** `BTreeIndex.searchCost` takes `log(0)` when the index has 0 blocks, so EXPLAIN shows about −2 billion blocks. Repro: `explain select eid, sectionid, grade from enroll where studentid = 4`. The same bug is behind the `blocks=7` on the B-tree index join in Step 2: (−2147483647 × 2 lookups) overflows to 2, and 1 + 2 + 4 = 7. Fix: use at least 1 block in `searchCost`.
