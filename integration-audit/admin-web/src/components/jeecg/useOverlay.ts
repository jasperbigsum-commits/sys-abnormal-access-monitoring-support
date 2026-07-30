export interface OverlayInstance<P extends object> {
    setProps: (props: Partial<P> & { open?: boolean }) => void;
}

export function useOverlay<P extends object = Record<string, never>>() {
    let instance: OverlayInstance<P> | undefined;
    const register = (registered: OverlayInstance<P>) => { instance = registered; };
    const setProps = (props: Partial<P> & { open?: boolean }) => instance?.setProps(props);
    return [register, {
        open: (props: Partial<P> = {}) => setProps({ ...props, open: true }),
        close: () => setProps({ open: false } as Partial<P> & { open: boolean }),
        setProps
    }] as const;
}
