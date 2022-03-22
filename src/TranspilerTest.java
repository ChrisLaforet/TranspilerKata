import static org.junit.Assert.assertEquals;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

public class TranspilerTest {

    @Rule
    public ErrorCollector collector = new ErrorCollector();
    
    public void expect (String actual, String expected) {
        assertEquals (expected, actual);
        //collector.checkThat (expected, CoreMatchers.equalTo (actual));
    }

    public void fromTo (String input, String expected) {
        expect (Transpiler.transpile (input), expected);
    }

    public void shouldFail (String input) {
        fromTo (input, "");
    }
    
    
    @Test
    public void testEmptyReturnsEmpty() {
        fromTo ("", "");
    }
    
    @Test
    public void testOneParensReturnsOneParens() {
        fromTo ("1()", "1()");
    }
    
    @Test
    public void testSomething() {
        fromTo ("123()", "123()");
        fromTo ("a()", "a()");
        fromTo ("abc()", "abc()");
    }
    
    @Test
    public void testWhen_there_is_no_lambda () {
        fromTo ("call()", "call()");
        shouldFail ("%^&*(");
        shouldFail ("x9x92xb29xub29bx120()!(");
        fromTo ("invoke  (       a    ,   b   )", "invoke(a,b)");
        fromTo ("invoke(a, b)", "invoke(a,b)");
    }
    
    @Test
    public void testWhen_there_are_lambda_expressions1 () {
        fromTo ("call({})", "call((){})");
    }
    
    
    @Test
    public void testWhen_there_are_lambda_expressions2 () {
        fromTo ("f({a->})", "f((a){})");
    }
    
    @Test
    public void testWhen_there_are_lambda_expressions3 () {
        fromTo ("f({a->a})", "f((a){a;})");
    }
    
    
    @Test
    public void testWhen_lambda_expressions_aren_t_inside_brackets1 () {
        fromTo ("call(\n){}", "call((){})");
    }
    
    @Test
    public void testWhen_lambda_expressions_aren_t_inside_brackets2 () {
        fromTo ("invoke  (       a    ,   b   ) { } ", "invoke(a,b,(){})");
        fromTo ("f(x){a->}", "f(x,(a){})");
        fromTo ("f(a,b){a->a}", "f(a,b,(a){a;})");
        fromTo ("run{a}", "run((){a;})");
    }
    
    @Test
    public void testWhen_invoking_a_lambda_directly () {
        fromTo ("{}()", "(){}()");
        fromTo ("{a->a}(233)", "(a){a;}(233)");
    }  
    
    @Test
    public void testWhen_function_has_lambda_argument() {
    	fromTo("fun {a -> a}", "fun((a){a;})");
    	fromTo("fun { a, b -> a b }", "fun((a,b){a;b;})");
    }
    
    @Test
    public void testExtended1() {
        fromTo ("(12,ab)c", "");
    }
    
    @Test
    public void testExtended2() {
        fromTo ("a b c", "");
    }
    
    @Test
    public void testExtended3() {
        fromTo ("f( a v)", "");
    }
    
    @Test
    public void testExtended4() {
        fromTo ("run(a){as we can}", "run(a,(){as;we;can;})> but was:<run(a,(as,we){can;})");
    }
    
    @Test
    public void testExtended5() {
        fromTo ("{a->a}(cde,y,z){x,y,d -> stuff}", "(a){a;}(cde,y,z,(x,y,d){stuff;})");
    }
    
    @Test
    public void testExtended6() {
        fromTo ("{}{}{}", "");
    }
}