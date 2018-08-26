package bbe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.core.dom.*;

import com.github.gumtreediff.utils.Pair;


@SuppressWarnings("unchecked")
public class DestASTVisitor extends ASTVisitor
{
	private HashMap<Integer, BlockVariableMap> expectedVars;
	private HashMap<Integer, BlockVariableMap> blockVars;
	private ArrayList<Pair<String, String>> updates;
	private String errorMessage;
	private boolean fatalError;

	
	public DestASTVisitor(HashMap<Integer, BlockVariableMap> expectedVars, ArrayList<Pair<String, String>> renames)
	{
		this.blockVars = new HashMap<Integer, BlockVariableMap>();
		this.blockVars.put(0, new BlockVariableMap(renames));
		this.expectedVars = expectedVars;
		this.updates = renames;
		/*Iterator<Entry<Integer, BlockVariableMap>> it = this.expectedVars.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry<Integer, BlockVariableMap> pair = (Map.Entry<Integer, BlockVariableMap>)it.next();
	        pair.getValue().renameVars(renames);
	    }*/
	}
	
	
	public HashMap<Integer, BlockVariableMap> getDeclaredVars() 
	{
		return this.blockVars;
	}

	public boolean visit(Block node)
	{
		int parentDepth = ASTNodeUtils.getBlockDepth(node.getParent());
		if (fatalError && !this.expectedVars.containsKey(parentDepth)) {
			errorMessage = "block missmatch (" + parentDepth + ")";
			return false;
		}
		this.blockVars.put(parentDepth + 1, new BlockVariableMap(this.blockVars.get(parentDepth), updates, node));
		return true;
	}

	public void endVisit(Block node) 
	{	
		int parentDepth = ASTNodeUtils.getBlockDepth(node.getParent());
		int currentDepth = parentDepth + 1;
		Iterator<Entry<String, Integer>> it = this.blockVars.get(currentDepth).entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry<String, Integer> pair = (Map.Entry<String, Integer>)it.next();
	        this.blockVars.get(parentDepth).computeIfPresent(pair.getKey(), (k, v) -> pair.getValue());
	    }
	    
	    ArrayList<String> conflictingVars = new ArrayList<String>();
	    boolean hasBlockConflicts = false;
	    it = this.expectedVars.get(currentDepth).entrySet().iterator();
	    while (it.hasNext()) {		    
	        Map.Entry<String, Integer> pair = (Map.Entry<String, Integer>)it.next();
	    	String name = pair.getKey();
	    	Pair<String, String> rename = this.blockVars.get(currentDepth).getRenamePair(name);
	    	name = rename != null ? rename.second : name;
	    	// TODO check KeyNotFoundException
			int srcValue = this.expectedVars.get(currentDepth).get(pair.getKey());
			int destValue = this.blockVars.get(currentDepth).get(name);
			if (srcValue != destValue) {
				Logger.logError("Different value of variable: " + pair.getKey() + "(" + srcValue + ") != " + name + "(" + destValue + ")");
				conflictingVars.add(pair.getKey());
				hasBlockConflicts = true;
			}
	    }
	    Logger.logInfo("Block (" + currentDepth + ") traversed with conflicts: " + hasBlockConflicts);
	    if (hasBlockConflicts)
	    {
	    	Logger.logInfo("Conflicting vars: " + conflictingVars.toString());
	    	Logger.logInfo("Deep diving into into block (" + currentDepth + ")");
		    Block sourceBlock = this.expectedVars.get(currentDepth).getCurrentBlock();
		    compareBlocks(sourceBlock, node, conflictingVars);
	    }
	}
	
	private boolean compareBlocks(Block src, Block dest, ArrayList<String> conflictingVars) 
	{
		int id = ASTNodeUtils.getBlockDepth(src);
		Logger.logInfo("Comparing blocks with id (" + id + ") with conflicting vars (" + conflictingVars + ")");
	    
	    List<Statement> srcStatements = src.statements();
	    List<Statement> destStatements = dest.statements();
		for (Statement statement : destStatements) {
			
		}
	    
		return true;
	}
	
	public boolean visit(VariableDeclarationStatement node) 
	{
		Type type = node.getType();
		for (Modifier modifier : (List<Modifier>)node.modifiers()) {
			// TODO check modifiers for each variable if they match in both files
			// if we want to support modifiers, then the var value in map has to be a class type :(
		}
		return true;
	}
	
	public boolean visit(VariableDeclarationFragment node) 
	{
		int blockHashCode = ASTNodeUtils.getBlockDepth(node);
		SimpleName name = node.getName();
		Expression expr = node.getInitializer();
		
		int value = 0;
		
		// If declaration without initialization
		if (expr == null)
			value = Integer.MAX_VALUE;
		else {
			// If right side is number ex. x = 5
			if (expr.getNodeType() == Type.NUMBER_LITERAL)
				value = Integer.parseInt(expr + "");
			// If right side is infix expression ex. x = a + b
			else if (expr.getNodeType() == Type.INFIX_EXPRESSION)
				value = visitInfix((InfixExpression)expr);
			// If right side is simple name ex. x = y
			else if (expr.getNodeType() == Type.SIMPLE_NAME) {
				// If it is variable and we have it in map
				if (this.blockVars.get(blockHashCode).containsKey(expr + ""))
					value = this.blockVars.get(blockHashCode).get(expr + "");
				else 
					value = Integer.MAX_VALUE;
			}
		}
		
		this.blockVars.get(blockHashCode).put(new String(name + ""), value);

		return false;
	}

	public boolean visit(Assignment node) 
	{
		int blockHashCode = ASTNodeUtils.getBlockDepth(node);
		String identifier = node.getLeftHandSide() + "";
		String operator = node.getOperator() + "";
		int value;
		
		// Case where we have number on the right side
		if (node.getRightHandSide().getNodeType() == Type.NUMBER_LITERAL)
			value = Integer.parseInt(node.getRightHandSide() + "");
		// Case where we have infix expression on the right side
		else if (node.getRightHandSide().getNodeType() == Type.INFIX_EXPRESSION) {
			value = visitInfix((InfixExpression)node.getRightHandSide());
		}
		//Case where we have one variable on the right side
		else {
			String rightSideIdentifier = node.getRightHandSide() + "";
			value = this.blockVars.get(blockHashCode).get(rightSideIdentifier);
		}
			
		int valueOfVar = 0;
		
		if (this.blockVars.get(blockHashCode).containsKey(identifier)) {
			valueOfVar = this.blockVars.get(blockHashCode).get(identifier);
			
			if (operator.equals("+="))
				this.blockVars.get(blockHashCode).replace(identifier, valueOfVar + value);
			else if (operator.equals("-="))
				this.blockVars.get(blockHashCode).replace(identifier, valueOfVar - value);
			else if (operator.equals("="))
				this.blockVars.get(blockHashCode).replace(identifier, value);
		}
	
		return true;
	}

	// Prefix expressions ex. ++x
	public boolean visit(PrefixExpression node)
	{
		int blockHashCode = ASTNodeUtils.getBlockDepth(node);
		String identifier = node.getOperand() + "";
		String operator = node.getOperator() + "";
		int valueOfVar = 0;
		
		if (this.blockVars.get(blockHashCode).containsKey(identifier)) {
			valueOfVar = this.blockVars.get(blockHashCode).get(identifier);
			
			if (operator.equals("++"))
				this.blockVars.get(blockHashCode).replace(identifier, valueOfVar + 1);
			else if (operator.equals("--"))
				this.blockVars.get(blockHashCode).replace(identifier, valueOfVar - 1);
		}

		return true;
	}
	
	// Postfix expressions ex. x++
	public boolean visit(PostfixExpression node)
	{
		int blockHashCode = ASTNodeUtils.getBlockDepth(node);
		String identifier = node.getOperand() + "";
		String operator = node.getOperator() + "";
		int valueOfVar = 0;
		
		if (this.blockVars.get(blockHashCode).containsKey(identifier)) {
			valueOfVar = this.blockVars.get(blockHashCode).get(identifier);
			
			if (operator.equals("++"))
				this.blockVars.get(blockHashCode).replace(identifier, valueOfVar + 1);
			else if (operator.equals("--"))
				this.blockVars.get(blockHashCode).replace(identifier, valueOfVar - 1);
		}

		return true;
	}
	
	// Infix expressions ex. x + y
	public int visitInfix(InfixExpression node)
	{
		int blockHashCode = ASTNodeUtils.getBlockDepth(node);
		String leftIdentifier = node.getLeftOperand() + "";
		String rightIdentifier = node.getRightOperand() + "";
		int leftSideValue = this.blockVars.get(blockHashCode).get(leftIdentifier) != null ? this.blockVars.get(blockHashCode).get(leftIdentifier) : Integer.parseInt(leftIdentifier);
		int rightSideValue = this.blockVars.get(blockHashCode).get(rightIdentifier) != null ? this.blockVars.get(blockHashCode).get(rightIdentifier) : Integer.parseInt(rightIdentifier);
		
		String operator = node.getOperator() + "";
		
		switch (operator) {
			case "+":
				return leftSideValue + rightSideValue;
			case "-":
				return leftSideValue - rightSideValue;
			case "*": 
				return leftSideValue * rightSideValue;
			case "/":
				return leftSideValue / rightSideValue;
			case "%":
				return leftSideValue % rightSideValue;
		}
		
		// Some dummy default return value, will never come here
		return 0;
	}
	
	public boolean visit(ReturnStatement node)
	{
		int value = 0;
		
		Expression expr = node.getExpression();
		
		// Calculating return value depending on expression type
		if (expr.getNodeType() == Type.NUMBER_LITERAL)
			value = Integer.parseInt(expr + "");
		else if (expr.getNodeType() == Type.INFIX_EXPRESSION)
			value = visitInfix((InfixExpression)expr);
		else if (expr.getNodeType() == Type.SIMPLE_NAME)
			value = this.blockVars.get(ASTNodeUtils.getBlockDepth(node)).get(expr + "");
		
		return true;
	}	
}
