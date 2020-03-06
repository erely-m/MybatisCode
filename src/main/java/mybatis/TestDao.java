package mybatis;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TestDao {

    Test find(String name);
}
