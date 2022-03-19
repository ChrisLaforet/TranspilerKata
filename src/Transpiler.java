import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public class Transpiler {
	public static String transpile(String expression) {
		try {
			final List<Token> tokens = Tokenizer.tokenize(expression);
			processLambdaExpressions(tokens);
			processFunctionalExpression(tokens);
			return tokens.stream().map(token -> token.getToken().toString()).collect(Collectors.joining());
		} catch (Exception ex) {
			return "";
		}
	}
	
	private static void processLambdaExpressions(List<Token> tokens) {
		boolean hasLambda = false;
		for (Token token : tokens) {
			if (token.getTokenType() == Token.TOKEN_OPENCURLY) {
				hasLambda = true;
				break;
			}
		}
		if (!hasLambda) {
			return;
		}
		final List<Token> updatedTokens = new ArrayList<>();
		Token lambdaToken = null;
		for (int index = 0; index < tokens.size(); ) {
			Token token = tokens.get(index++);
			if (token.getTokenType() == Token.TOKEN_OPENCURLY) {
//				if (lambdaToken != null) {
//					throw new IllegalStateException("Only one lambda is permitted per input");
//				}
				final List<Token> lambdaTokens = new ArrayList<>();
				do {
					token = tokens.get(index++);
					if (token.getTokenType() == Token.TOKEN_OPENCURLY) {
						throw new IllegalStateException("Nested lambdas are not permitted");
					} else if (token.getTokenType() == Token.TOKEN_CLOSECURLY) {
						break;
					}
					lambdaTokens.add(token);
				} while (index < tokens.size());

				lambdaToken = tokenizeLambda(lambdaTokens);
				updatedTokens.add(lambdaToken);
			} else {
				updatedTokens.add(token);
			}
		}
		
		tokens.clear();
		tokens.addAll(updatedTokens);
	}
	
	static private Token tokenizeLambda(List<Token> tokens) {
		final Lambda lambda = new Lambda();
		if (!tokens.isEmpty()) {
			int index = 0;
			Token token = tokens.get(index++);
			while (token.getTokenType() != Token.TOKEN_ARROW && index < tokens.size()) {
				if (token.getTokenType() == Token.TOKEN_NAME || token.getTokenType() == Token.TOKEN_NUMBER) {
					lambda.addParam(token.getToken().toString());
				} else if (token.getTokenType() != Token.TOKEN_COMMA) {
					throw new IllegalStateException("Invalid token in lambda");
				}
				token = tokens.get(index++);
			}
			
			if (token.getTokenType() == Token.TOKEN_ARROW) {
				token = (index < tokens.size()) ? tokens.get(index++) : null;
			}
			
			while (token != null) {
				if (token.getTokenType() == Token.TOKEN_NAME || token.getTokenType() == Token.TOKEN_NUMBER) {
					lambda.addStatement(token.getToken().toString());
				} else {
					throw new IllegalStateException("Invalid token in lambda");
				}
				if (index >= tokens.size() ) {
					break;
				}
				token = tokens.get(index++);				
			}
		}
		return new Token(Token.TOKEN_LAMBDA, lambda);
	}
	
	static private void processFunctionalExpression(List<Token> tokens) {
		if (tokens.size() <= 1) {
			return;
		}
		
		int index = 0;
		Token token = tokens.get(index++);
		if (token.getTokenType() != Token.TOKEN_NAME && token.getTokenType() != Token.TOKEN_NUMBER) {
			return;
		}

		final List<Token> updatedTokens = new ArrayList<>();
		updatedTokens.add(token);
		
		token = tokens.get(index++);
		if (token.getTokenType() == Token.TOKEN_LAMBDA) {
			updatedTokens.add(new Token(Token.TOKEN_OPENPAREN, "("));
			updatedTokens.add(token);
			updatedTokens.add(new Token(Token.TOKEN_CLOSEPAREN, ")"));
			
			for ( ; index < tokens.size(); index++) {
				updatedTokens.add(tokens.get(index));
			}
		} else if (token.getTokenType() == Token.TOKEN_OPENPAREN &&
				tokens.get(tokens.size() - 1).getTokenType() == Token.TOKEN_LAMBDA) {
			updatedTokens.add(token);
			for ( ; index < tokens.size() - 2; index++) {
				token = tokens.get(index);
				updatedTokens.add(token);
			}
			if (token.getTokenType() != Token.TOKEN_OPENPAREN) {
				updatedTokens.add(new Token(Token.TOKEN_COMMA, ","));
			}
			updatedTokens.add(tokens.get(index + 1));	// suck lambda into functional expression			
			updatedTokens.add(tokens.get(index));
		} else {
			return;
		}
		

		tokens.clear();
		tokens.addAll(updatedTokens);
	}
	
	static class Lambda {
		private List<String> params = new ArrayList<>();
		private List<String> statements = new ArrayList<>();
		
		public Lambda() {
		}
		
		public void addParam(String param) {
			params.add(param);
		}
		
		public void addStatement(String statement) {
			statements.add(statement);
		}
		
		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append("(");
			if (!params.isEmpty()) {
				sb.append(params.stream().map(String::toString).collect(Collectors.joining(",")));
			} 
			sb.append("){");
			if (!statements.isEmpty()) {
				sb.append(statements.stream().map(String::toString).collect(Collectors.joining(";", "", ";")));
			}
			sb.append("}");
			return sb.toString();
		}
	}

	static class Token {
		
		static final int TOKEN_EMPTY = 0;
		static final int TOKEN_NAME = 1;
		static final int TOKEN_NUMBER = 2;
		static final int TOKEN_EXPRESSION = 3;
		static final int TOKEN_OPENPAREN = 4;
		static final int TOKEN_CLOSEPAREN = 5;
		static final int TOKEN_COMMA = 6;
		static final int TOKEN_OPENCURLY = 7;
		static final int TOKEN_CLOSECURLY = 8;
		static final int TOKEN_ARROW = 9;
		static final int TOKEN_LAMBDA = 10;
		
		private Object token;
		private int tokenType;
		
		public Token(int tokenType, Object token) {
			this.token = token;
			this.tokenType = tokenType;
		}
		
		public Token(int tokenType) {
			this.token = null;
			this.tokenType = tokenType;
		}
		
		public Object getToken() {
			return token;
		}
		
		public int getTokenType() {
			return tokenType;
		}
		
	}
	
	static class Tokenizer {
		private List<Token> tokens = new ArrayList<>();
		private int lastTokenType = Token.TOKEN_EMPTY;
		private Deque<Integer> pairMatching = new ArrayDeque<>();
		
		private Tokenizer() { }
		
		private void emitToken(int tokenType, String token) {
			tokens.add(new Token(tokenType, token));
			lastTokenType = tokenType;
		}
		
		private void checkClosingPair(int tokenType) {
			if (pairMatching.isEmpty() || pairMatching.pop() != tokenType) {
				throw new IllegalStateException("Unexpected close paren or curly brace");
			}
		}
		
		private void tokenizeInput(String input) {
			StringBuilder token = new StringBuilder();
			int tokenType = Token.TOKEN_EMPTY;

			int offset = 0;
			while (offset < input.length()) {
				char ch = input.charAt(offset);
				
				switch (tokenType) {
					case Token.TOKEN_EMPTY:
						if (ch == '(') {
							if (tokens.isEmpty()) {
								throw new IllegalStateException("Open paren cannot start expression");
							}
							emitToken(Token.TOKEN_OPENPAREN, Character.toString(ch));
							pairMatching.push(Token.TOKEN_CLOSEPAREN);
						} else if (ch == ')') {
							if (lastTokenType == Token.TOKEN_COMMA) {
								throw new IllegalStateException("Comma cannot occur as last item in parens");
							}
							checkClosingPair(Token.TOKEN_CLOSEPAREN);
							emitToken(Token.TOKEN_CLOSEPAREN, Character.toString(ch));
						} else if (ch == '{') {
							emitToken(Token.TOKEN_OPENCURLY, Character.toString(ch));
							pairMatching.push(Token.TOKEN_CLOSECURLY);
						} else if (ch == '}') {
							checkClosingPair(Token.TOKEN_CLOSECURLY);
							emitToken(Token.TOKEN_CLOSECURLY, Character.toString(ch));
						} else if (ch == ',') {
							if (lastTokenType == Token.TOKEN_OPENPAREN) {
								throw new IllegalStateException("Comma cannot occur as first item in parens");
							}
							emitToken(Token.TOKEN_COMMA, Character.toString(ch));
						} else if (Character.isAlphabetic(ch) || ch == '_') {
							if (tokens.isEmpty() || !(tokens.size() == 1 && lastTokenType == Token.TOKEN_NAME)) {
								tokenType = Token.TOKEN_NAME;
								token.append(ch);
							} else {
								throw new IllegalStateException("Illegal use of name following function name");
							}
						} else if (Character.isDigit(ch)) {
							tokenType = Token.TOKEN_NUMBER;
							token.append(ch);
						} else if (ch == '-') {
							if (lastTokenType != Token.TOKEN_NAME && lastTokenType != Token.TOKEN_NAME) {
								throw new IllegalStateException("Arrow must follow name or number");
							}
							tokenType = Token.TOKEN_ARROW;
							token.append(ch);
						} else if (ch != ' ' && ch != '\n') {
							throw new IllegalStateException("Invalid character " + ch);
						}
						offset++;
						break;
						
					case Token.TOKEN_NUMBER:
						if (Character.isDigit(ch)) {
							token.append(ch);
							offset++;
						} else if (Character.isAlphabetic(ch) || ch == '_') {
							throw new IllegalStateException("Numbers cannot contain alpha or _ characters");
						} else {
							emitToken(tokenType, token.toString());
							tokenType = Token.TOKEN_EMPTY;
							token = new StringBuilder();
						}
						break;
						
					case Token.TOKEN_NAME:
						if (Character.isAlphabetic(ch) || Character.isDigit(ch) || ch == '_') {
							token.append(ch);
							offset++;
						} else {
							emitToken(tokenType, token.toString());
							tokenType = Token.TOKEN_EMPTY;
							token = new StringBuilder();
						}
						break;
						
					case Token.TOKEN_ARROW:
						if (ch == '>') {
							token.append(ch);
							emitToken(tokenType, token.toString());
							tokenType = Token.TOKEN_EMPTY;
							token = new StringBuilder();
							offset++;
						} else {
							throw new IllegalStateException("Malformed arrow token");
						}
				}
			}
			
			if (!pairMatching.isEmpty()) {
				throw new IllegalStateException("Unbalanced parens or braces");
			}
		}
		
		public List<Token> getTokens() {
			return tokens;
		}
		
		public static List<Token> tokenize(String input) {
			final Tokenizer tokenizer = new Tokenizer();
			tokenizer.tokenizeInput(input);
			return tokenizer.getTokens();
		}
	}
}
