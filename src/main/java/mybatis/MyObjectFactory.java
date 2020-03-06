package mybatis;

import org.apache.ibatis.reflection.factory.DefaultObjectFactory;

public class MyObjectFactory extends DefaultObjectFactory {

    @Override
    public <T> T create(Class<T> type) {
        if(type.equals(Test.class)){
            Test t = (Test) super.create(type);
            t.setValue("ces");
            return type.cast(t);
        }
        return super.create(type);
    }
}
