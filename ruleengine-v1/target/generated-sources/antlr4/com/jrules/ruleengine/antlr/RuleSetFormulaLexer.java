// Generated from com/jrules/ruleengine/antlr/RuleSetFormula.g4 by ANTLR 4.13.1
package com.jrules.ruleengine.antlr;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class RuleSetFormulaLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		AND=1, OR=2, NOT=3, IDENTIFIER=4, LPAREN=5, RPAREN=6, WS=7;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"AND", "OR", "NOT", "IDENTIFIER", "LPAREN", "RPAREN", "WS"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, null, null, "'('", "')'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "AND", "OR", "NOT", "IDENTIFIER", "LPAREN", "RPAREN", "WS"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}


	public RuleSetFormulaLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "RuleSetFormula.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\u0004\u0000\u0007,\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002\u0001"+
		"\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004"+
		"\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003"+
		"\u0005\u0003\u001d\b\u0003\n\u0003\f\u0003 \t\u0003\u0001\u0004\u0001"+
		"\u0004\u0001\u0005\u0001\u0005\u0001\u0006\u0004\u0006\'\b\u0006\u000b"+
		"\u0006\f\u0006(\u0001\u0006\u0001\u0006\u0000\u0000\u0007\u0001\u0001"+
		"\u0003\u0002\u0005\u0003\u0007\u0004\t\u0005\u000b\u0006\r\u0007\u0001"+
		"\u0000\t\u0002\u0000AAaa\u0002\u0000NNnn\u0002\u0000DDdd\u0002\u0000O"+
		"Ooo\u0002\u0000RRrr\u0002\u0000TTtt\u0003\u0000AZ__az\u0004\u000009AZ"+
		"__az\u0003\u0000\t\n\r\r  -\u0000\u0001\u0001\u0000\u0000\u0000\u0000"+
		"\u0003\u0001\u0000\u0000\u0000\u0000\u0005\u0001\u0000\u0000\u0000\u0000"+
		"\u0007\u0001\u0000\u0000\u0000\u0000\t\u0001\u0000\u0000\u0000\u0000\u000b"+
		"\u0001\u0000\u0000\u0000\u0000\r\u0001\u0000\u0000\u0000\u0001\u000f\u0001"+
		"\u0000\u0000\u0000\u0003\u0013\u0001\u0000\u0000\u0000\u0005\u0016\u0001"+
		"\u0000\u0000\u0000\u0007\u001a\u0001\u0000\u0000\u0000\t!\u0001\u0000"+
		"\u0000\u0000\u000b#\u0001\u0000\u0000\u0000\r&\u0001\u0000\u0000\u0000"+
		"\u000f\u0010\u0007\u0000\u0000\u0000\u0010\u0011\u0007\u0001\u0000\u0000"+
		"\u0011\u0012\u0007\u0002\u0000\u0000\u0012\u0002\u0001\u0000\u0000\u0000"+
		"\u0013\u0014\u0007\u0003\u0000\u0000\u0014\u0015\u0007\u0004\u0000\u0000"+
		"\u0015\u0004\u0001\u0000\u0000\u0000\u0016\u0017\u0007\u0001\u0000\u0000"+
		"\u0017\u0018\u0007\u0003\u0000\u0000\u0018\u0019\u0007\u0005\u0000\u0000"+
		"\u0019\u0006\u0001\u0000\u0000\u0000\u001a\u001e\u0007\u0006\u0000\u0000"+
		"\u001b\u001d\u0007\u0007\u0000\u0000\u001c\u001b\u0001\u0000\u0000\u0000"+
		"\u001d \u0001\u0000\u0000\u0000\u001e\u001c\u0001\u0000\u0000\u0000\u001e"+
		"\u001f\u0001\u0000\u0000\u0000\u001f\b\u0001\u0000\u0000\u0000 \u001e"+
		"\u0001\u0000\u0000\u0000!\"\u0005(\u0000\u0000\"\n\u0001\u0000\u0000\u0000"+
		"#$\u0005)\u0000\u0000$\f\u0001\u0000\u0000\u0000%\'\u0007\b\u0000\u0000"+
		"&%\u0001\u0000\u0000\u0000\'(\u0001\u0000\u0000\u0000(&\u0001\u0000\u0000"+
		"\u0000()\u0001\u0000\u0000\u0000)*\u0001\u0000\u0000\u0000*+\u0006\u0006"+
		"\u0000\u0000+\u000e\u0001\u0000\u0000\u0000\u0003\u0000\u001e(\u0001\u0006"+
		"\u0000\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}