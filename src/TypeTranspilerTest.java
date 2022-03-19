import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TypeTranspilerTest {

    private String tt(String s)                 { return new TypeTranspiler(s).transpile(); }
    private void   test(String inp, String exp) { assertEquals(exp, tt(inp));               }
    private void   keep(String s)               { test(s,s);                                }
    private void   gg(String s)                 { assertNull(tt(s));                        }
    
    
    @Test
    public void _01_SimpleTests_NothingShouldChange() {
      	keep("A");
      	keep("_");
      	gg("A.");
      	gg("A.123");
    }
    
    @Test
    public void _02_SimpleTests_GenericParameters() {
	      keep("A<A>");
	      gg("A<");
	      test("List<Int>","List<Integer>");
	      gg("A>");
	      test("Int.Companion","Integer.Companion");
	      gg("<A>");
    }
    
    @Test
    public void _03_SimpleTests_StarProjections() {
	      test("A<*>","A<?>");
	      test("A<*, *, A>","A<?,?,A>");
	      gg("?");
	      gg("*<A>");
    }
    
    @Test
    public void _04_SimpleTests_Variance() {
	      test("A<in A>","A<? super A>");
	      test("List<out T>","List<? extends T>");
	      test("Array<out CSharp, out Java>","Array<? extends CSharp,? extends Java>");
	      test("ArrayList<in out>","ArrayList<? super out>");
	      test("ArrayList<out in>","ArrayList<? extends in>");
	      keep("A<in>");
	      keep("Array<out>");
    }
    
    @Test
    public void _05_SimpleTests_RenameAppropriately() {
	      test("Int","Integer");
	      test("List<Int>","List<Integer>");
    }
    
    @Test
    public void _06_SimpleTests_RemoveSpaces() {
	      test("A<A, B>","A<A,B>");
    }
    
    @Test
    public void _07_SimpleTests_MutlipleGenericParameters() {
	      keep("A<A<A>>");
	      keep("A<A<A<A<A>>>>");
	      keep("A<A,B,C,D>");
    }
    
    // yes, there isn't any "_08_..." method ;p 
    
    @Test
    public void _09_ComplexTests_FunctionTypes() {
	      test("(A) -> B","Function1<A,B>");
	      test("(A, B) -> C","Function2<A,B,C>");
	      gg("() -> ()");
    }
    
    @Test
    public void _10_ComplexTests_ComplexFunctionTypes() {
	      test("((A) -> B) -> C","Function1<Function1<A,B>,C>");
	      test("(A) -> (B) -> C","Function1<A,Function1<B,C>>");
	      test("(((A) -> B) -> C) -> D","Function1<Function1<Function1<A,B>,C>,D>");
	      test("(A, B) -> C","Function2<A,B,C>");
	      test("((A) -> B, (B) -> C) -> (A) -> C","Function2<Function1<A,B>,Function1<B,C>,Function1<A,C>>");
    }

}
