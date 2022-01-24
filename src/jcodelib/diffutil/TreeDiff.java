package jcodelib.diffutil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.CompilationUnit;

import com.github.gumtreediff.actions.ActionGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.gen.Generators;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.ITree;

import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import ch.uzh.ifi.seal.changedistiller.ChangeDistiller.Language;
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import edu.fdu.se.cldiff.CLDiffLocal;
import file.FileIOManager;
import jcodelib.element.GTAction;
import jcodelib.util.CodeUtils;
import script.ScriptGenerator;
import script.model.EditScript;
import tree.Tree;
import tree.TreeBuilder;

public class TreeDiff {
	public static int runTime = 0;

	public static List<SourceCodeChange> diffChangeDistiller(File left, File right){
		long beforeTime = System.currentTimeMillis();
		FileDistiller distiller = ChangeDistiller.createFileDistiller(Language.JAVA);
		try {
			distiller.extractClassifiedSourceCodeChanges(left, right);
		} catch(Exception e) {
			/* An exception most likely indicates a bug in ChangeDistiller. Please file a
		       bug report at https://bitbucket.org/sealuzh/tools-changedistiller/issues and
		       attach the full stack trace along with the two files that you tried to distill. */
			System.err.println("Warning: error while change distilling. " + e.getMessage());
		}

		List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
		long afterTime = System.currentTimeMillis();
		int diffTime = (int)(afterTime - beforeTime);
		runTime = diffTime;
		List<SourceCodeChange> filteredChanges = new ArrayList<SourceCodeChange>();
		if(changes != null) {
			for(SourceCodeChange change : changes) {
				if(!change.getChangedEntity().getType().isComment()){
					filteredChanges.add(change);
				}
			}
		}
		
		return filteredChanges;
	}

	
	public static List<com.github.gumtreediff.actions.model.Action> diffGumTree(File srcFile, File dstFile) throws Exception {
		List<com.github.gumtreediff.actions.model.Action> actions = null;
		com.github.gumtreediff.client.Run.initGenerators();
		ITree src = Generators.getInstance().getTree(srcFile.getAbsolutePath()).getRoot();
		ITree dst = Generators.getInstance().getTree(dstFile.getAbsolutePath()).getRoot();
		Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
		m.match();
		ActionGenerator g = new ActionGenerator(src, dst, m.getMappings());
		g.generate();
		actions = g.getActions();

		return actions;
	}

//	public static List<GTAction> groupGumTreeActions(File srcFile, File dstFile, List<com.github.gumtreediff.actions.model.Action> actions) {
//		List<GTAction> gtActions = new ArrayList<GTAction>();
//		try {
//			CompilationUnit srcCu = CodeUtils.getCompilationUnit(FileIOManager.getContent(srcFile));
//			CompilationUnit dstCu = CodeUtils.getCompilationUnit(FileIOManager.getContent(dstFile));
//			//Group actions.
//			while(actions.size() > 0){
//				Action action = actions.get(0);
//				GTAction gtAction = new GTAction(action, srcCu, dstCu);
//				gtAction = attachActions(gtAction, actions, srcCu, dstCu);
//				gtActions.add(gtAction);
//			}
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//
//		return gtActions;
//	}

	public static List<GTAction> diffGumTreeWithGrouping(File srcFile, File dstFile) throws Exception {
		long beforeTime = System.currentTimeMillis();
		List<GTAction> gtActions = new ArrayList<GTAction>();
		com.github.gumtreediff.client.Run.initGenerators();
		ITree src = Generators.getInstance().getTree(srcFile.getAbsolutePath()).getRoot();
		ITree dst = Generators.getInstance().getTree(dstFile.getAbsolutePath()).getRoot();
		Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
		m.match();
		ActionGenerator g = new ActionGenerator(src, dst, m.getMappings());
		g.generate();
		List<com.github.gumtreediff.actions.model.Action> actions = g.getActions();
		long afterTime = System.currentTimeMillis();
		int diffTime = (int)(afterTime - beforeTime);
		
		CompilationUnit srcCu = CodeUtils.getCompilationUnit(FileIOManager.getContent(srcFile));
		CompilationUnit dstCu = CodeUtils.getCompilationUnit(FileIOManager.getContent(dstFile));
		//Group actions.
		while(actions.size() > 0){
			Action action = actions.get(0);
			GTAction gtAction = new GTAction(action, srcCu, dstCu, diffTime);
			gtAction = attachActions(gtAction, actions, srcCu, dstCu, diffTime);
			gtActions.add(gtAction);
		}
		return gtActions;
	}

	private static GTAction attachActions(
			GTAction gtAction, List<Action> actions, CompilationUnit srcCu, CompilationUnit dstCu, int TimeInput) {
		GTAction root = gtAction;

		//Bottom-up search to find a root action.
		GTAction parent;
		ITree parentNode;
		do {
			parent = null;
			parentNode = root.action.getNode().getParent();
			if (parentNode != null) {
				for (Action action : actions) {
					if (action.getNode().getId() == parentNode.getId()
							&& GTAction.getActionType(action).equals(root.actionType)) {
						parent = new GTAction(action, srcCu, dstCu, TimeInput);
						break;
					}
				}
			}
			//Switch the root.
			if(parent != null){
				root = parent;
			}
		} while (parent != null);

		//Top-down search for children.
		List<GTAction> targetActions = new ArrayList<GTAction>();
		List<GTAction> attachedActions = new ArrayList<GTAction>();
		targetActions.add(root);
		actions.remove(root.action);
		do {
			targetActions.addAll(attachedActions);
			attachedActions.clear();
			//Find children of each target.
			for(GTAction target : targetActions){
				for (ITree child : target.action.getNode().getChildren()) {
					for (Action action : actions) {
						if (action.getNode().getId() == child.getId()
								&& target.actionType.equals(GTAction.getActionType(action))) {
							GTAction gta = new GTAction(action, srcCu, dstCu, TimeInput);
							target.children.add(gta);
							attachedActions.add(gta);
							break;
						}
					}
				}
			}
			//Remove all attached actions.
			for(GTAction gta : attachedActions){
				actions.remove(gta.action);
			}
		} while (attachedActions.size() > 0 && actions.size() > 0);

		return root;
	}

	public static EditScript diffLAS(File srcFile, File dstFile){
		try {
			Tree before = TreeBuilder.buildTreeFromFile(srcFile);
			Tree after = TreeBuilder.buildTreeFromFile(dstFile);
			EditScript script = ScriptGenerator.generateScript(before, after);
			return script;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	public static void runCLDiff(File srcFile, File dstFile) {

	}

	public static void runCLDiff(String repo, String commitId, String outputDir) {
		CLDiffLocal CLDiffLocal = new CLDiffLocal();
		CLDiffLocal.run(commitId,repo,outputDir);
	}
}
