package bbe;

import java.util.HashMap;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;

public class ASTNodeUtils 
{
	public static final int ROOT_BLOCK_ID = 1 << 30;
	private static HashMap<Integer, Integer> _counters = new HashMap<>();
	
	
	public static int getBlockId(ASTNode node)
	{
		int depth = getBlockDepth(node);
		int order = _counters.get(depth) != null ? _counters.get(depth) : 0;	// _counters.get(depth) ?? 0  :<
		
		return ROOT_BLOCK_ID >>> depth + order;
	}
	
	public static void incrementBlockCount(Block node) 
	{
		int depth = getBlockDepth(node);
		int order = _counters.get(depth) != null ? _counters.get(depth) : 0;
		
		Logger.logInfo("Incrementing block counter on depth: " + depth + " | new value: " + (order + 1));
		_counters.put(depth, order + 1);
	}
	
	public static void decrementBlockCount(Block node) 
	{
		int depth = getBlockDepth(node);
		int order = _counters.get(depth) != null ? _counters.get(depth) : 0;
		
		Logger.logInfo("Decrementing block counter on depth: " + depth + " | new value: " + (order > 0 ? order - 1 : 0));
		_counters.put(depth, order > 0 ? order - 1 : 0);
	}
	
	public static String getContainingMethodName(ReturnStatement node)
	{
		MethodDeclaration decl = getParentMethodDeclaration(node);
		return "$" + decl.getName().toString();
	}
	
	public static int getBlockDepth(ASTNode node)
	{
		if (node == null)
			return 0;

		if (node instanceof Block) {
			return 1 + getBlockDepth(node.getParent());
		}
		
		return getBlockDepth(node.getParent());	
	}
	
	public static void resetCounters()
	{
		_counters.clear();
	}
	
	private static MethodDeclaration getParentMethodDeclaration(ASTNode node) 
	{
		if (node == null)
			return null;
		
		if (node instanceof MethodDeclaration)
			return (MethodDeclaration)node;
		
		return getParentMethodDeclaration(node.getParent());
	}
}
