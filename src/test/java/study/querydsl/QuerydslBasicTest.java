package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import java.util.List;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.*;

@SpringBootTest
@Transactional

public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;
    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL() {
        //member1을 찾아라
        String sqlString = "select m from Member m" +
                            "where m.username=:username";
        Member findMember = em.createQuery(sqlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    /**
     * runtimeerror가 뜨던 jpsl 과 달리 compiler 시점에서 Querydsl에러 잡음
     */
        public void rydsl(){

        Member findMember = queryFactory
                .select(member)
                .from(member)
                //알아서 파라미터 바인딩을 해줌
                .where(member.username.eq("member1"))
                //하나를 조회할때 fetchOne
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10)))
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetchTest() {
//        List<Member> fetch = queryFactory
//                .selectFrom(member)
//                .fetch();
//        Member fetchOne = queryFactory.selectFrom(QMember.member)
//                .fetchOne();
//
//        Member fetchFirst = queryFactory
//                .selectFrom(QMember.member)
//                .fetchFirst();

//        QueryResults<Member> results = queryFactory.selectFrom(member)
//                .fetchResults();
//
//        results.getTotal();
//        List<Member> content = results.getResults();

        long total = queryFactory
                .selectFrom(member)
                .fetchCount();
    }

    /**
     * 회원 정렬순서
     * 1. 회원 내림차순(desc)
     * 2. 회원 이름 올림차순(asc)
     *3.단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     */
    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc()
                        , member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();

    }

    /**
     * 팀의 이름과 각 팀의 평균을 구해라
     */
    @Test
    public void group() throws Exception {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15); //(10 + 20) /2 = 15

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);//(30 + 40) /2 = 35
    }


    /**
     * 팀 A에 소속된 모든 회원
     */
    @Test
    public void join() {
        List<Member> result = queryFactory
                .selectFrom(member)
                //left join, right join 다 가능!!!inner join 등등
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /**
     * 세타 조인
     * 회원의 이름이 팀이름과 같은 이름을 조회
     * 외부 조인 불가능 0> 다음에 설명할 조인 on을 사용하면 외부 조인 가능
     */
    @Test
    public void theta_join() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /**
     * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     *JPQL: select m, t from Member m left join m.team t on t.name = "teamA"
     *
     * tuple = [Member(id=3, username=member1, age=10), Team(id=1, name=teamA)]
     * tuple = [Member(id=4, username=member2, age=20), Team(id=1, name=teamA)]
     * tuple = [Member(id=5, username=member3, age=30), null]
     * tuple = [Member(id=6, username=member4, age=40), null]
     */
    @Test
    public void join_on_filtering() {
        //튜플로 나온 이유 = select 가 member, team등 여러가지 타입으로 나옴
        List<Tuple> result = queryFactory.select(member, team)
                .from(member)
                .leftJoin(member.team, team)
                .on(team.name.eq("teamA"))
                //join이 leftJoin이 아니라 일반 join 일 경우
//                .where(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 연관관계가 없는 엔티티 외부조인
     * 회원의 이름이 팀 이름과 같은 대상을 외부 조인
     */
    @Test
    public void join_on_no_realation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                //세타 조인일 경우 left 조인이 안됨
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();

    }

    @Test
    public void fetchJoinUse() {
        em.flush();
        em.clear();

        Member findMember;
        findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }


    /**
     * 나이가 가장 많은 회원 조회
     */
    @Test
    public void subQuery() {

        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();
        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /**
     * 나이가 평균 이상인  회원
     */
    @Test
    public void subQueryGoe() {

        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();
        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    /**
     * 나이가 평균 이상인  회원
     * 나이가 10 초과하는 사람안에 속하는 거 속출
     * where 안에서 사용 but select 절에도 서브쿼리 사용 가능
     */
    @Test
    public void subQueryIn() {

        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();
        assertThat(result).extracting("age")
                .containsExactly( 20, 30, 40);
    }

    /**
     * tuple = [member1, 25.0]
     * tuple = [member2, 25.0]
     * tuple = [member3, 25.0]
     * tuple = [member4, 25.0]
     * 얘도 static import를 할 수 있음
     *
     * from 절의 서브쿼리는 지원하지 않은
     * QueryDSL 은 물론 JPQL 에서 모두다 안된다
     * QueyrDsl JPQL빌더 역할을 하는 거기 때문에.... 같이 안된다
     *
     * from절의 서브쿼리 해결 방안
     * 서브쿼리를 join으로 변경(가능한 상황도 있고, 불가능한 상환도 있다.)
     * 애플리케이션에서 쿼리를 2번 분리해서 실행한다.
     * nativeSQL 을 사용한다
     */
    @Test
    public void selectSubquery() {

        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
                .select(member.username,
                        select(memberSub.age.avg())
                                .from(memberSub)
                ).from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * s = 열살
     * s = 스무살
     * s = 기타
     * s = 기타
     */
    @Test
    public void basicCase() {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void complexCase() {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("나이 더 많아"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void constant() {
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 하 얘 왜 안되는지 모르겠다.......
     * fetch1 = Member(id=3, username=member1, age=6548)
     * fetch1 = Member(id=4, username=member2, age=20)
     * s = member1_6
     * s = member2_2
     */
    @Test
    public void concat() {
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);

        }
    }

    /**
     * 리스트타이과 셀렉트절에 member도 삽입 가능
     */
    @Test
    public void simpleProjection() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("age = " + age);
            System.out.println("username = " + username);


        }
    }
    
    @Test
    public void findDtoByJPQL() {
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test//property접근법
    public void findDtoBySetter() {
        List<MemberDto> fetch = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        /**
         * QueryDsl이 memberdto를 만들고 값을 setset
         * dto를 만들려고 하다 보니 기본생성자를 호출해야됨
         * 고로 기본생성자가 없으면 에러
         */
        for (MemberDto memberDto : fetch) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test//property접근법 -> 값들이 바로 필드에 꽂힘 -> 프라이빗이라도 꽂을 수 있음
    public void findDtoByField() {
        List<MemberDto> fetch = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : fetch) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test//property접근법 -> 값들이 바로 필드에 꽂힘 -> 프라이빗이라도 꽂을 수 있음
    public void findUserDto() {
        QMember memberSub = new QMember("memberSub");
        List<UserDto> fetch = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"),

                        //subquery 처리법
                        ExpressionUtils.as(JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub), "age")
                        ))
                .from(member)
                .fetch();

        for (UserDto userDto : fetch) {
            System.out.println("userDto = " + userDto);

        }

    }

    @Test//constructor 접근법
    /**
     * Constructor 형태는 Dto 담긴 속성보다 더 많은 값을 파라미터로 받으면
     * 실행은 되나 RunTime Error 발생 compile오류 잡지 못함
     */
    public void findDtoByConstructor() {
        List<UserDto> fetch = queryFactory
                .select(Projections.constructor(UserDto.class,
                        member.username,
                        member.age))
                //      member.id
                .from(member)
                .fetch();

        for (UserDto memberDto : fetch) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * construct형태와 달리 더 많은 값이 파라미터로 들어올 경우 compile오류가 남
     * 그나마 위에보다 더 안전함 ctrl + P로 속성확인 가능
     * Q파일 생성해야 한다는 단점이 있음
     * @QueryProjection 넣어주어야 함
     * MemberDto가 Querydsl의 의존성을 가지게됨 -> 라이브러리를 뺄 경우 코드들이 영향을 받음
     * 아키텍처적임 고민을 해봐야 됨...
     */
    @Test
    public void findDtoByQueryProjection() {
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void dynamicQuery_BooleanBuilder() {
        String usernameParam = "member1";
        Integer ageParam = 10;

       List<Member> result = searchMember1(usernameParam, ageParam);
       assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {

        BooleanBuilder builder= new BooleanBuilder();
        if(usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }

        if(ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    /**
     * 동적 쿼리 where 방식 -> builder를 이용할때 보다 코드가 깔끔
     */
    @Test
    public void dynamicQuery_WhereParam() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                //where에 null이되면 무시가 됨 -> 동적쿼리 가능
//                .where(usernameEq(usernameCond), ageEq(ageCond))
                .where(allEq(usernameCond, ageCond))
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    /**
     * 수정, 삭제 배치 쿼리(벌크 연산)
     *
     */

    @Test
    /**
     * bulk 연산자는 영속성 컨텍스트 무시하고 바로 DB에 바로 쿼리 뿌림
     * DB 와 영속성 상태가 다름
     * 영속성 상태
     *
     */
    public void bulkUpdate() {
        //==영속성==           ==DB==
        //member1 = 10 -> member1
        //member2 = 20 -> member2
        //member3 = 30 -> member3
        //member4 = 40 -> member4
        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();
        em.flush();
        em.clear();
        //==영속성==       ==DB==
        //member1 = 10 -> 비회원
        //member2 = 20 -> 비회원
        //member3 = 30 -> member3
        //member4 = 40 -> member4

        /**
         * 현재 영속성과 DB는 다른상태
         * 이럴경우 DB에서 비회원, 비회원을 가져오더라도
         * 영속성 컨텍스트가 우선건을 가짐으로
         * 전부 member1, 2, 3, 4를 가져옴
         * DB에는 여전히 비회원, 비회원, member3, member4
         */
        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();
        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
    }
    
    @Test
    public void bulkAdd() {
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.multiply(2))
                .execute();
    }

    @Test
    public void bulkDelete() {
        queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }

    @Test
    public void sqlFunction() {
        List<String> result = queryFactory
                .select(Expressions.stringTemplate(
                        "function('replace',{0},{1}, {2})",
                        member.username, "member", "M")
                ).from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void sqlFunction2() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(
//                        Expressions.stringTemplate("function('lower', {0})", member.username)))
                .where(member.username.eq(member.username.lower()))
                .fetch();

        System.out.println("result = " + result);
    }
}
