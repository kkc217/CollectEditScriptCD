package jcodelib.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class TreeBuilder {

	public static TreeNode buildTreeFromFile(File f) throws IOException {
		TreeNode tree = buildTreeFromSource(readContent(f));

		return tree;
	}

	public static TreeNode buildTreeFromSource(String source) throws IOException {
		TreeNode root = new TreeNode();
		CompilationUnit cu = getCompilationUnit(source);
		JavaCodeVisitor visitor = new JavaCodeVisitor(root);
		cu.accept(visitor);

		return root;
	}

	public static CompilationUnit getCompilationUnit(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS11);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		CompilationUnit cu = (CompilationUnit)parser.createAST(null);

		return cu;
	}

	private static String readContent(File f) throws IOException {
		StringBuffer sb = new StringBuffer();
		String content;
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
		char[] cbuf = new char[512];
		int len = 0;
		while((len=br.read(cbuf))>-1){
			sb.append(cbuf, 0, len);
		}
		br.close();
		content = sb.toString();
		return content;
	}

}
