An example AQL query for cycle detection is as follows.

```
FOR start IN knows
    FOR vertex, edge, path
        IN 2..10
        OUTBOUND start._from
        GRAPH socialNetwork
        FILTER edge._to == start._from
        RETURN CONCAT_SEPARATOR("->", path.vertices[*].id)
```

The profiling results are listed below.

```
Query String (217 chars, cacheable: false):
 FOR start IN knows
     FOR vertex, edge, path
         IN 2..10
         OUTBOUND start._from
         GRAPH socialNetwork
         FILTER edge._to == start._from
         RETURN CONCAT_SEPARATOR("->", path.vertices[*].id)

Execution plan:
 Id   NodeType                  Site  Calls   Items   Runtime [s]   Comment
  1   SingletonNode             DBS       1       1       0.00004   * ROOT
  2   EnumerateCollectionNode   DBS       2    1999       0.00063     - FOR start IN knows   /* full collection scan, 1 shard(s)  */
  3   CalculationNode           DBS       2    1999       0.00046       - LET #6 = start.`_from`   /* attribute expression */   /* collections used: start : knows */
 11   RemoteNode                COOR      4    1999       0.00456       - REMOTE
 12   GatherNode                COOR      4    1999       0.00078       - GATHER   /* unsorted */
  4   TraversalNode             COOR      3       0     114.81897       - FOR vertex  /* vertex */, edge  /* edge */, path  /* paths: vertices */ IN 2..10  /* min..maxPathDepth */ OUTBOUND #6 /* startnode */  GRAPH 'socialNetwork'
  5   CalculationNode           COOR      3       0       0.00005         - LET #10 = (edge.`_to` == start.`_from`)   /* simple expression */   /* collections used: start : knows */
  6   FilterNode                COOR      3       0       0.00005         - FILTER #10
  7   CalculationNode           COOR      3       0       0.00004         - LET #12 = CONCAT_SEPARATOR("->", path.`vertices`[*].`id`)   /* simple expression */
  8   ReturnNode                COOR      3       0       0.00005         - RETURN #12

Indexes used:
 By   Name   Type   Collection   Unique   Sparse   Selectivity   Fields        Ranges
  4   edge   edge   knows        false    false         4.25 %   [ `_from` ]   base OUTBOUND

Traversals on graphs:
 Id  Depth  Vertex collections  Edge collections  Options                                  Filter / Prune Conditions           
 4   2..10  person              knows             uniqueVertices: none, uniqueEdges: path  FILTER (edge.`_to` == start.`_from`)

Optimization rules applied:
 Id   RuleName
  1   move-calculations-up
  2   move-filters-up
  3   move-calculations-up-2
  4   move-filters-up-2
  5   optimize-traversals
  6   remove-redundant-path-var
  7   scatter-in-cluster
  8   distribute-filtercalc-to-cluster
  9   remove-unnecessary-remote-scatter

Query Statistics:
 Writes Exec   Writes Ign   Scan Full   Scan Index   Filtered   Peak Mem [b]   Exec Time [s]
           0            0        1999       828554          0         819200       114.82801

Query Profile:
 Query Stage           Duration [s]
 initializing               0.00001
 parsing                    0.00047
 optimizing ast             0.00001
 loading collections        0.00001
 instantiating plan         0.00004
 optimizing plan            0.00147
 executing                114.82567
 finalizing                 0.00033
```