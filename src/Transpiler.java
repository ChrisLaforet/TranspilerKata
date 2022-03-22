import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public class Transpiler {
	public static String transpile(String expression) {
		try {
			final List<Token> tokens = Tokenizer.tokenize(expression);
			coalesceLambdaExpressions(tokens);
			ingestTrailingLambdaExpressions(tokens);
			return processFunction(tokens);
		} catch (Exception ex) {
			return "";
		}
	}
	
	private static void coalesceLambdaExpressions(List<Token> tokens) {
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
			
			boolean hasArrow = tokens.stream().anyMatch(token -> token.getTokenType() == Token.TOKEN_ARROW);
			
			int index = 0;
			Token token = tokens.get(index++);
			boolean nameOrNumberExpected = true;
			while (hasArrow && 
					token.getTokenType() != Token.TOKEN_ARROW && 
					index < tokens.size()) {
				if (token.getTokenType() == Token.TOKEN_NAME || token.getTokenType() == Token.TOKEN_NUMBER) {
					if (!nameOrNumberExpected) {
						throw new IllegalStateException("Comma expected instead of NameOrNumber");
					}
					lambda.addParam(token.getToken().toString());
					nameOrNumberExpected = false;
				} else if (token.getTokenType() == Token.TOKEN_COMMA) {
					if (nameOrNumberExpected) {
						throw new IllegalStateException("NameOrNumber expected instead of comma");
					}
					nameOrNumberExpected = true;
				} else {
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
	
	static private void ingestTrailingLambdaExpressions(List<Token> tokens) {
		// handle this src format: function ::= expression "(" [parameters] ")" lambda
		if (tokens.size() < 4 ||
				!tokens.stream().anyMatch(token -> token.getTokenType() == Token.TOKEN_LAMBDA)) {
			return;
		}
		
		int penultimate = tokens.size() - 2;
		final Token penultimateToken = tokens.get(penultimate);
		final Token lastToken = tokens.get(penultimate + 1);
		
		
		if (penultimateToken.getTokenType() != Token.TOKEN_CLOSEPAREN ||
				lastToken.getTokenType() != Token.TOKEN_LAMBDA) {
			return;
		}
		
		final List<Token> updatedTokens = new ArrayList<>();
		Token currentToken = null;
		for (int index = 0; index < penultimate; index++) {
			currentToken = tokens.get(index);
			updatedTokens.add(currentToken);
		}
		if (currentToken.getTokenType() != Token.TOKEN_OPENPAREN) {
			updatedTokens.add(new Token(Token.TOKEN_COMMA, ","));
		}
		updatedTokens.add(lastToken);		// flip behavior
		updatedTokens.add(penultimateToken);
		
		tokens.clear();
		tokens.addAll(updatedTokens);
	}
	
	static private String processFunction(List<Token> tokens) {
		final FunctionEmitter emitter = new FunctionEmitter(tokens);
		
		final Token token = emitter.getNext();
		processExpression(emitter, token);
		
		final Token nextToken = emitter.peekNext();
		if (nextToken == null && token.getTokenType() != Token.TOKEN_LAMBDA) {
			throw new IllegalStateException("A function name cannot stand alone");
		} else if (nextToken.getTokenType() == Token.TOKEN_NAME || 
				nextToken.getTokenType() == Token.TOKEN_NUMBER) {
			throw new IllegalStateException("A function cannot be followed by another name or number");
		}
		
		// function ::= expression "(" [parameters] ")"

		emitter.emit("(");
		processParameters(emitter);
		emitter.emit(")");
		if (emitter.peekNext() != null) {
			throw new IllegalStateException("A function cannot be followed by anything other than a lambda");
		}
		return emitter.toString();
	}
	
	static private void processExpression(FunctionEmitter emitter, Token token) {
		// expression ::= nameOrNumber | lambda
		if (token == null || (token.getTokenType() != Token.TOKEN_NAME && 
				token.getTokenType() != Token.TOKEN_NUMBER && 
				token.getTokenType() != Token.TOKEN_LAMBDA)) {
			throw new IllegalStateException("Invalid token for function");
		}
		emitter.emit(token);
	}
	
	static private void processParameters(FunctionEmitter emitter) {
		emitter.swallowTokenIfMatchesType(Token.TOKEN_OPENPAREN);
		boolean expressionExpected = true;
		while (true) {
			final Token token = emitter.getNext();
			if (token == null || token.getTokenType() == Token.TOKEN_CLOSEPAREN) {
				break;
			}
			if (token.getTokenType() == Token.TOKEN_COMMA) {
				if (expressionExpected) {
					throw new IllegalStateException("Invalid position for comma in parameter list");
				}
				emitter.emit(token);
				expressionExpected = true;
			} else if (expressionExpected) {
				processExpression(emitter, token);
				expressionExpected = false;
			} else {
				throw new IllegalStateException("Invalid order of parameters and commas");
			}
		}
	}
	
	static class FunctionEmitter {
		private StringBuilder sb = new StringBuilder();
		private int index = 0;
		private List<Token> tokens;
		
		public FunctionEmitter(List<Token> tokens) {
			this.tokens = tokens;
		}
		
		public Token peekNext() {
			if (index >= tokens.size() ) {
				return null;
			}
			return tokens.get(index);
		}
		
		public Token getNext() {
			final Token token = peekNext();
			if (token != null) {
				++index;
			}
			return token;			
		}
		
		public void swallowTokenIfMatchesType(int tokenType) {
			final Token token = peekNext();
			if (token != null && token.getTokenType() == tokenType) {
				++index;
			}
			
		}
		
		public void emit(String element) {
			sb.append(element);
		}
		
		public void emit(Token token) {
			sb.append(token.getToken().toString());
		}
		
		@Override
		public String toString() {
			return sb.toString();
		}
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
			// lambda ::= "(" [lambdaparam] "){" [lambdastmt] "}"
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
							if (lastTokenType != Token.TOKEN_NAME && lastTokenType != Token.TOKEN_NUMBER) {
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
			
			if (tokenType == Token.TOKEN_ARROW) {
				throw new IllegalStateException("Malformed arrow token");
			} else if (tokenType == Token.TOKEN_NAME || tokenType == Token.TOKEN_NUMBER) {
				emitToken(tokenType, token.toString());
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
