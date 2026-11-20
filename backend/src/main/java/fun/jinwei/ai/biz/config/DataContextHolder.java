package fun.jinwei.ai.biz.config;

/**
 * ThreadLocal 当前数据上下文 (个人/团队)
 */
public class DataContextHolder {

    private static final ThreadLocal<DataContext> context = new ThreadLocal<>();

    public static void set(DataContext ctx) { context.set(ctx); }
    public static DataContext get() { return context.get(); }
    public static void clear() { context.remove(); }

    public static Long getUserId() {
        DataContext ctx = get();
        return ctx != null ? ctx.userId() : null;
    }

    public static Long getTeamId() {
        DataContext ctx = get();
        return ctx != null ? ctx.teamId() : null;
    }

    public static boolean isTeamContext() {
        DataContext ctx = get();
        return ctx != null && ctx.teamId() != null;
    }

    public record DataContext(Long userId, Long teamId) {
        public DataContext(Long userId) {
            this(userId, null);
        }
    }
}
