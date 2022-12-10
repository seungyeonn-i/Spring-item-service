- 서블릿은 지원하는 exception 방식
    - Exception
    - response.sendError(HTTP status code, error message)
- Exception
    - 자바 직접 실행에서 Exception (기본 메커니즘)
        - 자바 메인 메서드 실행시, main 이라는 이름의 스레드 실행, 실행 도중 예외를 잡지 못하고 main 스레드 넘어서 예외가 던져지면, 에외 정보 넘기고 스레드 종료
    - 웹 애플리케이션
        - 사용자 요청별로 별도의 스레드가 할당, 서블릿 컨테이너 안에서 실행
        - try ~ catch 로 예외 잡아서 처리하면 문제 없다. 하지만, 예외를 미처 잡지 못하고 서블릿 밖으로 나가면 WAS까지 전달 가능
        
        <aside>
        💨 WAS(여기까지 전달)  ← filter ← servlet ← interceptor ← controller (예외 발생)
        
        </aside>
        
- 서블릿 예외처리 - 오류페이지 작동 원리
    - 예외가 WAS까지 전달되면 WAS는 오류페이지 정보를 다음코드로 확인한다.
        
        ```java
        @Component
        public class WebServerCustomizer implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
            @Override
            public void customize(ConfigurableWebServerFactory factory) {
        
                ErrorPage errorPage404 = new ErrorPage(HttpStatus.NOT_FOUND, "/error-page/404");
                ErrorPage errorPage500 = new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/error-page/500");
        
                ErrorPage errorPageEx = new ErrorPage(RuntimeException.class, "/error-page/500");
        
                factory.addErrorPages(errorPage404, errorPage500, errorPageEx);
            }
        }
        ```
        
    - 확인해보니 RuntimeException의 오류페이지로 ‘/error-page/500이 지정되어있다. WAS는 오류페이지 출력을 위해 지정된 /error-page/500를 다시 요청한다.
    - 오류 페이지 요청 흐름
        
        <aside>
        💨 WAS ‘/error-page/500’ 다시 요청 → filter → servlet → interceptor → controller(/error-page/500) → view
        
        </aside>
        
    - 정리하면 다음과 같다.
    **1. 예외가 발생해 WAS까지 전파된다. 
    2. WAS는 오류 페이지 경로를 찾아서 내부에서 오류페이지를 호출한다. 이 때 오류 페이지 경로로 필터,서블릿,인터셉터,컨트롤러가 모두 다시 호출된다.**
    - WAS는 오류페이지를 단순히 요청만 하는것이 아니라 오류정보를 request의 attribute에 추가해서 넘겨준다.
- filter / interceptor
    
    ![스크린샷 2022-12-09 오후 3.30.22.png](https://s3-us-west-2.amazonaws.com/secure.notion-static.com/022a1997-23e7-454f-a6df-9731aaa026ec/%E1%84%89%E1%85%B3%E1%84%8F%E1%85%B3%E1%84%85%E1%85%B5%E1%86%AB%E1%84%89%E1%85%A3%E1%86%BA_2022-12-09_%E1%84%8B%E1%85%A9%E1%84%92%E1%85%AE_3.30.22.png)
    
    - Webconfig
        
        ```java
        @Configuration
        public class WebConfig implements WebMvcConfigurer {
        
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new LogInterceptor())
                        .order(1)
                        .addPathPatterns("/**")
                        .**excludePathPatterns**("/css/**", "*.ico", "/error", "/error-page/**"); // 오류 페이지 경로
            }
        
        	//    @Bean
            public FilterRegistrationBean logFilter() {
                FilterRegistrationBean<Filter> filterRegistrationBean = new FilterRegistrationBean<>();
                filterRegistrationBean.setFilter(new LogFilter());
                filterRegistrationBean.setOrder(1);
                filterRegistrationBean.addUrlPatterns("/*");
                **filterRegistrationBean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR);**
                // 이 필터는 dispatcherType이 request,error일 때 호출 됩니다.
                return filterRegistrationBean;
            }
        
        ```
        
    - LogFilter
        
        ```java
        public class LogFilter implements Filter {
            @Override
            public void init(FilterConfig filterConfig) throws ServletException {
                Filter.super.init(filterConfig);
            }
        
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                chain.doFilter(request, response); // 필수 
            }
        
            @Override
            public void destroy() {
                Filter.super.destroy();
            }
        }
        ```
        
    - LogInterceptor : 로그 위치 입력한 interceptor, HandlerInterceptor 인터페이스의 필수 구현체 preHandle, postHandle,afterCompletion은 꼭 오버라이딩 해야함
        
        ```java
        public class LogInterceptor implements HandlerInterceptor {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                return HandlerInterceptor.super.preHandle(request, response, handler);
            }
        
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
                HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
            }
        
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
                HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
            }
        }
        ```
        
- 전체 흐름 정리
    
    <aside>
    💨 1. WAS(/error-ex, dispatchType=REQUEST) -> 필터 -> 서블릿 -> 인터셉터 -> 컨트롤러
    2. WAS(여기까지 전파) <- 필터 <- 서블릿 <- 인터셉터 <- 컨트롤러(예외발생)
    3. WAS 오류 페이지 확인
    4. WAS(/error-page/500, dispatchType=ERROR) -> 필터(x) -> 서블릿 -> 인터셉터(x) ->
    컨트롤러(/error-page/500) -> View
    
    </aside>
    
    - 4번의 필터(x), 인터셉터 (x)는 dispathcerType, excludePathPatterns에 따라 결정
