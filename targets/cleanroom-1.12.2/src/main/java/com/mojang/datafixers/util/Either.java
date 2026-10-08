package com.mojang.datafixers.util;

import java.util.function.Consumer;
import java.util.function.Function;

public final class Either<L, R> {
    private final L left;
    private final R right;
    private Either(L left, R right) { this.left = left; this.right = right; }
    public static <L, R> Either<L, R> left(L value) { return new Either<>(value, null); }
    public static <L, R> Either<L, R> right(R value) { return new Either<>(null, value); }
    public boolean leftPresent() { return left != null; }
    public boolean rightPresent() { return right != null; }
    public L left() { return left; }
    public R right() { return right; }
    public <T> T map(Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper) { return left != null ? leftMapper.apply(left) : rightMapper.apply(right); }
    public void ifLeft(Consumer<? super L> consumer) { if (left != null) consumer.accept(left); }
    public void ifRight(Consumer<? super R> consumer) { if (right != null) consumer.accept(right); }
}
