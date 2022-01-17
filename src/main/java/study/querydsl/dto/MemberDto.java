package study.querydsl.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data // tostring까지 자동적으로 만들어 줌
@NoArgsConstructor
public class MemberDto {

    private String username;
    private int age;

    public MemberDto(String username, int age) {
        this.username = username;
        this.age = age;
    }
}
