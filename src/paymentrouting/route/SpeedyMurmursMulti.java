package paymentrouting.route;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import gtna.graph.Graph;
import gtna.graph.Node;
import gtna.graph.spanningTree.SpanningTree;
import gtna.transformation.spanningtree.MultipleSpanningTree;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import paymentrouting.route.DistanceFunction.Timelock;
import treeembedding.treerouting.TreeCoordinates;
import treeembedding.vouteoverlay.Treeembedding;

/**
 * InterdimensionalSpeedyMurmurs 
 * @author mephisto
 *
 */
public class SpeedyMurmursMulti extends DistanceFunction {
	int[][][] coords; //node coordinates: one vector per tree and node 
	int[][] levels; // node level in tree: one integer per tree and node
	HopDistance hop;
	int[] roots;
	/**
	 * basic constructor without timeout 
	 * @param t
	 */
	public SpeedyMurmursMulti(int t) {
		super("SPEEDYMURMURS_MULTI_"+t, t, 1);
		this.coords = new int[t][][];
		this.levels = new int[t][];
	}
	
	/**
	 * timelockmode included, if mode != CONST, no value needed  
	 * @param t
	 */
	public SpeedyMurmursMulti(int t, Timelock lockMode) {
		super("SPEEDYMURMURS_MULTI_"+t+"_"+lockMode.toString(), t, 1, lockMode);
		this.coords = new int[t][][];
		this.levels = new int[t][];
	}
	
	/**
	 * timelockmode included, can be CONST for this constructor  
	 * @param t
	 */
	public SpeedyMurmursMulti(int t, Timelock lockMode, int lockval) {
		super("SPEEDYMURMURS_MULTI_"+t+"_"+lockMode.toString()+"_"+lockval, t, 1, lockMode, lockval);
		this.coords = new int[t][][];
		this.levels = new int[t][];
	}

	@Override
	/**
	 * distance is the minimal distance for all spanning trees 
	 */
	public double distance(int a, int b, int r) {
		double min = Integer.MAX_VALUE;
		for (int j = 0; j < this.realities; j++) {
			double d = distOne(a,b,j);
			if (d < min) {
				min = d;
			}
		}		
		return min;
	}

	/**
	 * distance between a and b in tree j 
	 * same as SpeedyMurmurs.distance()
	 * @param a
	 * @param b
	 * @param j
	 * @return
	 */
	private double distOne(int a, int b, int j) {
		int[] cA = this.coords[j][a];
		int[] cB = this.coords[j][b];
		int cpl = 0;
		for (int i = 0; i < levels[j][a]+1; i++) {
			if (cA[i] == cB[i]) {
				cpl++;
			} else {
				break;
			}
		}
		return levels[j][a]+levels[j][b]-2*cpl;

	}

	/**
	 * for attack: parent of i in tree k
	 * @return
	 */
	public int[][] parents() {
		int[][] result = new int[this.coords.length][];
		for (int i = 0; i < this.coords.length; i++)
			result[i] = parents(i);
		return result;
	}
	private int[] parents(int k) {
		return IntStream.range(0, this.coords[k].length)
				.map(a -> parent(a, k)).toArray();
	}

	private int parent(int a, int k) {
		int[] cA = this.coords[k][a];
		for (int b = 0; b < coords[k].length; b++) {
			if ((int) distOne(a, b, k) == 1 && levels[k][b] + 1 == levels[k][a]) {
				return b;
			}
		}
		return -1;
	}

	public int[][] levels() {
		return levels;
	}

	/**
	 * for attack: dist to first root
	 * @param a
	 * @return
	 */
	public int rootDist(int a, int k) {
		if (a == roots[k])
			return 0;
		return (int) distOne(a, roots[k], k);
	}


	@Override
	/**
	 * init spanning trees and coordinates
	 * in contrast to SpeedyMurmurs.initRouteInfo, this one removes old trees always, which is less error-prone but less efficient  
	 */
	public void initRouteInfo(Graph g, Random rand) {
		Node[] nodes = g.getNodes();
		//remove old trees
		int j = 0; 
		while (g.hasProperty("SPANNINGTREE_"+j)) {
			g.removeProperty("SPANNINGTREE_"+j);
			if (g.hasProperty("TREE_COORDINATES_"+j)) {
				g.removeProperty("TREE_COORDINATES_"+j);
			}
			j++; 
		}
		
		//choose roots
		int[] roots = new int[this.realities];
		for (int i = 0; i < roots.length; i++) {
			roots[i] = rand.nextInt(nodes.length);
		}
		this.roots = roots;

		//embed
		Treeembedding embed = new Treeembedding("T",60,roots, 
					 MultipleSpanningTree.Direct.TWOPHASE);
			  g = embed.transform(g);

			  hop = new HopDistance();
			  hop.initRouteInfo(g, null);
		 for (int i = 0; i < this.realities; i++) {
			 SpanningTree tree = (SpanningTree)g.getProperty("SPANNINGTREE_"+i);
			 this.levels[i] = tree.depth;
			 this.coords[i] = ((TreeCoordinates)g.getProperty("TREE_COORDINATES_"+i)).getCoords();
			 //todo rootfind
			 int rootIndex = tree.getSrc();
			 System.out.println("ROOT---- "+rootIndex);
//			 Set[] observations = new Set[nodes.length];
//			 for (int node = 0; node < nodes.length; node++) {
//				 int rootDist = (int) distOne(node, roots[i], i);
//				 Set<Integer> possible = new HashSet<>();
//				 for (int poss = 0; poss < nodes.length; poss++) {
//				 	if (rootDist == hop.distance(node, poss, 0)) {
//				 		possible.add(poss);
//					}
//				 }
//				 observations[node] = possible;
//			 }
//			 int sampleSize = 20;
////			 for (int colluders: new int[]{1, 2, 5, 10, 20}){
//////				 int colluders = 10;
//////				 System.out.println();
////				 for (int a = 0; a < sampleSize; a++) {
//////					 double avg = 0;
////					 double avg = Double.MAX_VALUE/2;
////					 Set<Integer> result = new HashSet<>(
////							 IntStream.range(0, nodes.length).mapToObj(Integer::new).collect(
////									 Collectors.toList()));
////					 for (int b = 0; b < colluders; b++) {
////						 Set<Integer> obs = observations[rand.nextInt(nodes.length)];
//////						 avg += obs.size();
////						 avg = Math.min(avg, obs.size());
////						 result.retainAll(obs);
////					 }
//////					 avg /= colluders;
////					 System.out.println(colluders+";\t"+result.size()+", obs;\t"+avg);
////				 }
////			 }
//			 int[] freqTot = new int[nodes.length];
//			 int[] freqDir = new int[nodes.length];
//			 for(int n = 0; n < nodes.length; n++) {
//				 int finalN = n;
//				 long children = IntStream.range(0, nodes.length).filter(a -> isChild(finalN,a)).count();
//				 long childrenDir = IntStream.range(0, nodes.length).filter(a -> isChild(finalN,a)
//						 && hop.distance(finalN,a,0)>1).count();
//				 freqTot[(int) children]++;
//				 freqDir[(int) childrenDir]++;
//			 }
//			 for(int n = 0; n < nodes.length; n++) {
//				 if (freqTot[n] > 0 || freqDir[n] > 0)
//					 System.out.println(n + " t-> " + (float)freqTot[n]*100f/nodes.length+ " d-> " + (float)freqDir[n]*100f/nodes.length);
//			 }
		 }


		  
	}

	@Override
	/**
	 * isCloser returns true if a is closer than b to dst in at least one tree
	 * note that we can have isCloser(a,b,dst,r) = true AND isCloser(b,a,dst,r) = true
	 * thus, we need loop detection as closer nodes are potential next hops and previous nodes are not automatically excluded 
	 */
	public boolean isCloser(int a, int b, int dst, int r) {
		boolean closer = false;
		for (int j = 0; j < this.realities; j++) {
			double dA = this.distOne(a, dst, j);
			double dB = this.distOne(b, dst, j);
			if (dA < dB) {
				closer = true;
				break;
			}
		}
		return closer; 
	}
	
}