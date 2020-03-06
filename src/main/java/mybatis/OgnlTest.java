package mybatis;

import ognl.Ognl;
import ognl.OgnlContext;

public class OgnlTest {
    public static void main(String[] args) throws Exception {
        Blog blog = new Blog();
        blog.setId(111);
        blog.setAuthor_id(1112);
        blog.setTitle("This is title！");
        OgnlContext context = new OgnlContext();
        context.put("blog", blog);
        context.put("test","1234");
        Object o1 = Ognl.parseExpression("blog != null");
        Object value = Ognl.getValue(o1, context);
        Object o2 = Ognl.parseExpression("test");
        Object value1 = Ognl.getValue(o2, context);
        System.out.println(value);
        System.out.println(value1);

    }
}
